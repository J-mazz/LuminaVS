"""
Lumina Virtual Studio - AI Orchestrator
Python Logic Director using Qwen 3 1.7B for semantic intent parsing

This module implements the Micro-DAG (Directed Acyclic Graph) for processing
user intents through the local Qwen model using llama-cpp-python.
"""

import json
import os
import time
import importlib
from functools import partial
from typing import Optional, Dict, Any, List
from pathlib import Path

from graph import DAGNode, execute_dag
from intent_types import AIIntent, IntentAction, RenderMode, EffectType, clamp_confidence
from pipeline import (
    preprocess_input,
    classify_intent,
    extract_parameters,
    validate_intent,
    finalize_intent,
)
from classifiers import rule_based_classify
from config import OrchestratorConfig

# Shared model filename for consistency with Kotlin downloader
MODEL_FILENAME = "qwen3-1.7b-instruct-q4_k_m.gguf"

# Lazy import for llama-cpp to avoid hard dependency during tests
try:
    _llama_mod = importlib.import_module("llama_cpp")
    Llama = getattr(_llama_mod, "Llama")
    LLAMA_AVAILABLE = True
except Exception:
    Llama = None  # type: ignore
    LLAMA_AVAILABLE = False
    print("llama-cpp-python not available, using mock mode")




class LuminaOrchestrator:
    """
    Main orchestrator class for AI-driven intent parsing.
    Uses a Micro-DAG architecture for processing user input.
    """
    
    # System prompt for Qwen model
    SYSTEM_PROMPT = """You are an AI assistant for Lumina Virtual Studio, a video effects application.
Parse user requests into structured intents. Respond ONLY with valid JSON.

Tool/model routing guidance:
- Prefer rule-based classification when confidence is already high (>= 0.9); only use the LLM when confidence is lower.
- When using the LLM, keep responses concise and strictly follow the JSON schema. Do not include explanations.
- Render modes: passthrough/normal, stylized, segmented, depth_map, normal_map.
- Effects: blur, bloom, color_grade, vignette, chromatic_aberration, noise, sharpen. If user requests intensity without a value, default to 0.5.
- Camera actions (capture, start_recording, stop_recording) should be high confidence; if ambiguous, return help.
- If the user asks what you can do, return action=help.

Available actions:
- set_render_mode: Change rendering style (passthrough, stylized, segmented, depth_map, normal_map)
- add_effect: Add visual effect (blur, bloom, color_grade, vignette, chromatic_aberration, noise, sharpen)
- remove_effect: Remove an effect
- adjust_parameter: Modify effect intensity or parameters
- capture_frame: Take a screenshot
- start_recording / stop_recording: Video recording
- reset: Reset all settings
- help: Show help

Response format:
{"action": "<action>", "target": "<target>", "parameters": "<json_params>", "confidence": <0.0-1.0>}

Examples:
User: "Make it look dreamy"
{"action": "add_effect", "target": "bloom", "parameters": "{\"intensity\": 0.7}", "confidence": 0.85}

User: "Show depth"
{"action": "set_render_mode", "target": "depth_map", "parameters": "{}", "confidence": 0.95}
"""

    MAX_LLM_TOKENS = 96  # keep inference light for mobile

    def __init__(self, assets_path: Optional[str] = None, config: Optional[OrchestratorConfig] = None):
        """Initialize the orchestrator"""
        self.assets_path = assets_path or "/data/data/com.lumina.engine/files"
        self.model: Any = None
        self.grammar_path: Optional[str] = None
        self.initialized = False
        self.config = config or OrchestratorConfig()
        self.last_context: Optional[Dict[str, Any]] = None
        
        # Build the Micro-DAG
        self._build_dag()
        
        # Intent history for context
        self.intent_history: List[AIIntent] = []
        self.max_history = 10
    
    def _build_dag(self):
        """Build the Micro-DAG processing pipeline"""
        self.execution_order = ["preprocess", "classify", "extract", "validate", "finalize"]
        self.dag_nodes: Dict[str, DAGNode] = {
            "preprocess": DAGNode(
                name="preprocess",
                processor=partial(preprocess_input, self)
            ),
            "classify": DAGNode(
                name="classify",
                processor=partial(classify_intent, self),
                dependencies=["preprocess"]
            ),
            "extract": DAGNode(
                name="extract",
                processor=partial(extract_parameters, self),
                dependencies=["classify"]
            ),
            "validate": DAGNode(
                name="validate",
                processor=partial(validate_intent, self),
                dependencies=["extract"]
            ),
            "finalize": DAGNode(
                name="finalize",
                processor=partial(finalize_intent, self),
                dependencies=["validate"]
            )
        }
    
    def initialize(self, assets_path: Optional[str] = None) -> bool:
        """
        Initialize the Qwen model from assets.
        
        Args:
            assets_path: Path to assets directory containing model file
            
        Returns:
            True if initialization successful
        """
        if assets_path:
            self.assets_path = assets_path
        
        model_path = os.path.join(self.assets_path, MODEL_FILENAME)
        grammar_path = os.path.join(self.assets_path, "qwen_grammar.gbnf")
        
        if os.path.exists(grammar_path):
            self.grammar_path = grammar_path
        
        if not LLAMA_AVAILABLE or Llama is None:
            print("Running in mock mode - llama-cpp-python not available")
            self.initialized = True
            return True
        
        if not os.path.exists(model_path):
            print(f"Model not found at {model_path}, running in mock mode")
            self.initialized = True
            return True
        
        try:
            print(f"Loading Qwen model from {model_path}")
            self.model = Llama(
                model_path=model_path,
                n_ctx=2048,
                n_threads=4,
                n_gpu_layers=0,  # CPU only for mobile
                verbose=False
            )
            self.initialized = True
            print("Model loaded successfully")
            return True
        except Exception as e:
            print(f"Failed to load model: {e}")
            self.initialized = True  # Fall back to mock mode
            return True
    
    def parse_intent(self, user_input: str) -> AIIntent:
        """
        Parse user input into a structured intent.
        
        Args:
            user_input: Natural language input from user
            
        Returns:
            AIIntent with parsed action, target, and parameters
        """
        if not self.initialized:
            self.initialize()
        # Run through the Micro-DAG
        context = {"input": user_input, "timestamp": int(time.time() * 1000)}
        try:
            context = execute_dag(self.dag_nodes, self.execution_order, user_input, context)
            intent = context.get(
                "intent",
                AIIntent(
                    action=IntentAction.UNKNOWN.value,
                    target=user_input,
                    confidence=0.0,
                    timestamp=context["timestamp"],
                ),
            )
        except Exception as exc:  # Defensive: never propagate to caller
            print(f"Orchestrator DAG execution failed: {exc}")
            intent = AIIntent(
                action=IntentAction.UNKNOWN.value,
                target=user_input,
                confidence=0.1,
                timestamp=context["timestamp"],
            )
            context["error"] = str(exc)
        finally:
            self.last_context = context

        self._add_to_history(intent)

        return intent

    def query_llm(self, normalized_input: str) -> Optional[Dict]:
        """Query the Qwen model for intent parsing"""
        if self.model is None:
            return None

        prompt = f"{self.SYSTEM_PROMPT}\n\nUser: {normalized_input}\nAssistant:"

        response = self.model(
            prompt,
            max_tokens=self.config.max_llm_tokens,
            temperature=0.1,
            top_p=0.9,
            stop=["User:", "\n\n"],
        )
        
        text = response["choices"][0]["text"].strip()
        
        try:
            start = text.find("{")
            end = text.rfind("}") + 1
            if start >= 0 and end > start:
                json_str = text[start:end]
                return json.loads(json_str)
        except json.JSONDecodeError:
            pass
        
        return None

    # Compatibility wrappers for legacy tests
    def _preprocess_input(self, user_input: str, context: Dict) -> Dict:
        return preprocess_input(self, user_input, context)

    def _rule_based_classify(self, normalized: str) -> Dict:
        return rule_based_classify(normalized)

    def _validate_intent(self, user_input: str, context: Dict) -> Dict:
        return validate_intent(self, user_input, context)
    
    def _query_llm(self, normalized_input: str) -> Optional[Dict]:
        """Query the Qwen model for intent parsing"""
        prompt = f"{self.SYSTEM_PROMPT}\n\nUser: {normalized_input}\nAssistant:"
        
        response = self.model(
            prompt,
            max_tokens=150,
            temperature=0.1,
            top_p=0.9,
            stop=["User:", "\n\n"]
        )
        
        text = response["choices"][0]["text"].strip()
        
        # Parse JSON response
        try:
            # Find JSON in response
            start = text.find("{")
            end = text.rfind("}") + 1
            if start >= 0 and end > start:
                json_str = text[start:end]
                return json.loads(json_str)
        except json.JSONDecodeError:
            pass
        
        return None
    
    
    def _add_to_history(self, intent: AIIntent):
        """Add intent to history for context"""
        self.intent_history.append(intent)
        if len(self.intent_history) > self.max_history:
            self.intent_history.pop(0)
    
    def get_history(self) -> List[Dict]:
        """Get intent history as list of dicts"""
        return [i.to_dict() for i in self.intent_history]
    
    def shutdown(self):
        """Cleanup resources"""
        if self.model:
            del self.model
            self.model = None
        self.initialized = False
        self.last_context = None


# Global orchestrator instance for JNI access
_orchestrator: Optional[LuminaOrchestrator] = None


def initialize(assets_path: str) -> bool:
    """Initialize the orchestrator (called from Kotlin)"""
    global _orchestrator
    _orchestrator = LuminaOrchestrator(assets_path)
    return _orchestrator.initialize()


def parse_intent(user_input: str) -> str:
    """Parse intent and return JSON (called from Kotlin)"""
    global _orchestrator
    if _orchestrator is None:
        _orchestrator = LuminaOrchestrator()
        _orchestrator.initialize()
    
    intent = _orchestrator.parse_intent(user_input)
    return intent.to_json()


def shutdown():
    """Shutdown the orchestrator (called from Kotlin)"""
    global _orchestrator
    if _orchestrator:
        _orchestrator.shutdown()
        _orchestrator = None


# Test function
def _test():
    """Test the orchestrator"""
    orch = LuminaOrchestrator()
    orch.initialize()
    
    test_inputs = [
        "Make it look dreamy",
        "Show me the depth map",
        "Add some blur, about 50%",
        "Take a screenshot",
        "Reset everything",
        "Help me understand what I can do"
    ]
    
    for inp in test_inputs:
        intent = orch.parse_intent(inp)
        print(f"Input: {inp}")
        print(f"Intent: {intent.to_json()}")
        print()
    
    orch.shutdown()


if __name__ == "__main__":
    _test()
