"""
Lumina Virtual Studio - AI Orchestrator
Python Logic Director using Qwen 2.5 1.5B for semantic intent parsing

This module implements the Micro-DAG (Directed Acyclic Graph) for processing
user intents through the local Qwen model using llama-cpp-python.
"""

import json
import os
import time
from dataclasses import dataclass, asdict
from enum import Enum
from typing import Optional, Dict, Any, List, Callable
from pathlib import Path

# Will be imported when running in Chaquopy environment
try:
    from llama_cpp import Llama
    LLAMA_AVAILABLE = True
except ImportError:
    LLAMA_AVAILABLE = False
    print("llama-cpp-python not available, using mock mode")


class IntentAction(Enum):
    """Supported intent actions"""
    SET_RENDER_MODE = "set_render_mode"
    ADD_EFFECT = "add_effect"
    REMOVE_EFFECT = "remove_effect"
    ADJUST_PARAMETER = "adjust_parameter"
    CAPTURE_FRAME = "capture_frame"
    START_RECORDING = "start_recording"
    STOP_RECORDING = "stop_recording"
    RESET = "reset"
    HELP = "help"
    UNKNOWN = "unknown"


class RenderMode(Enum):
    """Render modes matching Kotlin/C++ definitions"""
    PASSTHROUGH = 0
    STYLIZED = 1
    SEGMENTED = 2
    DEPTH_MAP = 3
    NORMAL_MAP = 4


class EffectType(Enum):
    """Effect types matching Kotlin/C++ definitions"""
    NONE = 0
    BLUR = 1
    BLOOM = 2
    COLOR_GRADE = 3
    VIGNETTE = 4
    CHROMATIC_ABERRATION = 5
    NOISE = 6
    SHARPEN = 7


@dataclass
class AIIntent:
    """Intent result structure matching Kotlin/C++ AIIntent"""
    action: str = ""
    target: str = ""
    parameters: str = ""
    confidence: float = 0.0
    timestamp: int = 0
    
    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)
    
    def to_json(self) -> str:
        return json.dumps(self.to_dict())


@dataclass
class DAGNode:
    """Node in the Micro-DAG processing pipeline"""
    name: str
    processor: Callable[[str, Dict], Dict]
    dependencies: List[str] = None
    
    def __post_init__(self):
        if self.dependencies is None:
            self.dependencies = []


class LuminaOrchestrator:
    """
    Main orchestrator class for AI-driven intent parsing.
    Uses a Micro-DAG architecture for processing user input.
    """
    
    # System prompt for Qwen model
    SYSTEM_PROMPT = """You are an AI assistant for Lumina Virtual Studio, a video effects application.
Parse user requests into structured intents. Respond ONLY with valid JSON.

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

    def __init__(self, assets_path: str = None):
        """Initialize the orchestrator"""
        self.assets_path = assets_path or "/data/data/com.lumina.engine/files"
        self.model: Optional[Llama] = None
        self.grammar_path: Optional[str] = None
        self.initialized = False
        
        # Build the Micro-DAG
        self._build_dag()
        
        # Intent history for context
        self.intent_history: List[AIIntent] = []
        self.max_history = 10
    
    def _build_dag(self):
        """Build the Micro-DAG processing pipeline"""
        self.dag_nodes: Dict[str, DAGNode] = {
            "preprocess": DAGNode(
                name="preprocess",
                processor=self._preprocess_input
            ),
            "classify": DAGNode(
                name="classify",
                processor=self._classify_intent,
                dependencies=["preprocess"]
            ),
            "extract": DAGNode(
                name="extract",
                processor=self._extract_parameters,
                dependencies=["classify"]
            ),
            "validate": DAGNode(
                name="validate",
                processor=self._validate_intent,
                dependencies=["extract"]
            ),
            "finalize": DAGNode(
                name="finalize",
                processor=self._finalize_intent,
                dependencies=["validate"]
            )
        }
    
    def initialize(self, assets_path: str = None) -> bool:
        """
        Initialize the Qwen model from assets.
        
        Args:
            assets_path: Path to assets directory containing model file
            
        Returns:
            True if initialization successful
        """
        if assets_path:
            self.assets_path = assets_path
        
        model_path = os.path.join(self.assets_path, "qwen-2.5-1.5b-instruct-q4_k_m.gguf")
        grammar_path = os.path.join(self.assets_path, "qwen_grammar.gbnf")
        
        if os.path.exists(grammar_path):
            self.grammar_path = grammar_path
        
        if not LLAMA_AVAILABLE:
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
        
        # Execute DAG in topological order
        execution_order = ["preprocess", "classify", "extract", "validate", "finalize"]
        
        for node_name in execution_order:
            node = self.dag_nodes[node_name]
            context = node.processor(user_input, context)
        
        intent = context.get("intent", AIIntent(
            action=IntentAction.UNKNOWN.value,
            target=user_input,
            confidence=0.0,
            timestamp=context["timestamp"]
        ))
        
        # Add to history
        self._add_to_history(intent)
        
        return intent
    
    def _preprocess_input(self, user_input: str, context: Dict) -> Dict:
        """Preprocess and normalize user input"""
        # Lowercase and strip
        normalized = user_input.lower().strip()
        
        # Remove common filler words
        fillers = ["please", "can you", "could you", "i want to", "i'd like to"]
        for filler in fillers:
            normalized = normalized.replace(filler, "").strip()
        
        context["normalized_input"] = normalized
        context["original_input"] = user_input
        return context
    
    def _classify_intent(self, user_input: str, context: Dict) -> Dict:
        """Classify the intent using the Qwen model or rule-based fallback"""
        normalized = context.get("normalized_input", user_input.lower())
        
        # Try LLM first if available
        if self.model is not None:
            try:
                llm_result = self._query_llm(normalized)
                if llm_result:
                    context["llm_result"] = llm_result
                    return context
            except Exception as e:
                print(f"LLM query failed: {e}")
        
        # Rule-based fallback
        context["rule_based"] = True
        context["classification"] = self._rule_based_classify(normalized)
        return context
    
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
    
    def _rule_based_classify(self, normalized: str) -> Dict:
        """Rule-based intent classification fallback"""
        result = {
            "action": IntentAction.UNKNOWN.value,
            "target": "",
            "parameters": {},
            "confidence": 0.5
        }
        
        # Render mode keywords
        render_modes = {
            "passthrough": ["normal", "passthrough", "original", "clear"],
            "stylized": ["stylize", "artistic", "style", "paint"],
            "segmented": ["segment", "separate", "isolate", "mask"],
            "depth_map": ["depth", "3d", "distance"],
            "normal_map": ["normal", "surface", "bumps"]
        }
        
        for mode, keywords in render_modes.items():
            if any(kw in normalized for kw in keywords):
                result["action"] = IntentAction.SET_RENDER_MODE.value
                result["target"] = mode
                result["confidence"] = 0.75
                return result
        
        # Effect keywords
        effects = {
            "blur": ["blur", "soft", "smooth", "fuzzy"],
            "bloom": ["bloom", "glow", "dreamy", "ethereal"],
            "color_grade": ["color", "grade", "tint", "warm", "cool"],
            "vignette": ["vignette", "border", "frame", "dark edges"],
            "chromatic_aberration": ["chromatic", "rgb split", "glitch"],
            "noise": ["noise", "grain", "film", "vintage"],
            "sharpen": ["sharpen", "crisp", "detail", "enhance"]
        }
        
        for effect, keywords in effects.items():
            if any(kw in normalized for kw in keywords):
                if "remove" in normalized or "off" in normalized:
                    result["action"] = IntentAction.REMOVE_EFFECT.value
                else:
                    result["action"] = IntentAction.ADD_EFFECT.value
                result["target"] = effect
                result["confidence"] = 0.7
                
                # Extract intensity if mentioned
                intensity = self._extract_intensity(normalized)
                if intensity is not None:
                    result["parameters"] = {"intensity": intensity}
                
                return result
        
        # Control keywords
        if any(kw in normalized for kw in ["capture", "screenshot", "photo", "snap"]):
            result["action"] = IntentAction.CAPTURE_FRAME.value
            result["confidence"] = 0.9
        elif any(kw in normalized for kw in ["record", "start recording", "video"]):
            result["action"] = IntentAction.START_RECORDING.value
            result["confidence"] = 0.85
        elif any(kw in normalized for kw in ["stop", "end recording"]):
            result["action"] = IntentAction.STOP_RECORDING.value
            result["confidence"] = 0.85
        elif any(kw in normalized for kw in ["reset", "clear", "default"]):
            result["action"] = IntentAction.RESET.value
            result["confidence"] = 0.9
        elif any(kw in normalized for kw in ["help", "what can", "how to"]):
            result["action"] = IntentAction.HELP.value
            result["confidence"] = 0.95
        
        return result
    
    def _extract_intensity(self, text: str) -> Optional[float]:
        """Extract intensity value from text"""
        import re
        
        # Look for percentage
        match = re.search(r'(\d+)\s*%', text)
        if match:
            return int(match.group(1)) / 100.0
        
        # Look for descriptive intensity
        if any(w in text for w in ["subtle", "light", "slight", "little"]):
            return 0.3
        elif any(w in text for w in ["medium", "moderate", "normal"]):
            return 0.5
        elif any(w in text for w in ["strong", "heavy", "intense", "max"]):
            return 0.8
        
        return None
    
    def _extract_parameters(self, user_input: str, context: Dict) -> Dict:
        """Extract detailed parameters from the classification"""
        if "llm_result" in context:
            llm = context["llm_result"]
            params_str = llm.get("parameters", "{}")
            try:
                context["parameters"] = json.loads(params_str) if isinstance(params_str, str) else params_str
            except:
                context["parameters"] = {}
            context["action"] = llm.get("action", IntentAction.UNKNOWN.value)
            context["target"] = llm.get("target", "")
            context["confidence"] = llm.get("confidence", 0.5)
        elif "classification" in context:
            cls = context["classification"]
            context["action"] = cls["action"]
            context["target"] = cls["target"]
            context["parameters"] = cls["parameters"]
            context["confidence"] = cls["confidence"]
        
        return context
    
    def _validate_intent(self, user_input: str, context: Dict) -> Dict:
        """Validate the extracted intent"""
        action = context.get("action", IntentAction.UNKNOWN.value)
        target = context.get("target", "")
        
        # Validate action is known
        valid_actions = [a.value for a in IntentAction]
        if action not in valid_actions:
            context["action"] = IntentAction.UNKNOWN.value
            context["confidence"] = min(context.get("confidence", 0.5), 0.3)
        
        # Validate render mode targets
        if action == IntentAction.SET_RENDER_MODE.value:
            valid_modes = [m.name.lower() for m in RenderMode]
            if target.lower() not in valid_modes:
                context["confidence"] = min(context.get("confidence", 0.5), 0.4)
        
        # Validate effect targets
        if action in [IntentAction.ADD_EFFECT.value, IntentAction.REMOVE_EFFECT.value]:
            valid_effects = [e.name.lower() for e in EffectType]
            if target.lower() not in valid_effects:
                context["confidence"] = min(context.get("confidence", 0.5), 0.4)
        
        context["validated"] = True
        return context
    
    def _finalize_intent(self, user_input: str, context: Dict) -> Dict:
        """Finalize and create the AIIntent object"""
        params = context.get("parameters", {})
        params_json = json.dumps(params) if params else "{}"
        
        intent = AIIntent(
            action=context.get("action", IntentAction.UNKNOWN.value),
            target=context.get("target", ""),
            parameters=params_json,
            confidence=context.get("confidence", 0.0),
            timestamp=context.get("timestamp", int(time.time() * 1000))
        )
        
        context["intent"] = intent
        return context
    
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
