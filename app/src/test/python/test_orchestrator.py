"""
Unit tests for Lumina Virtual Studio - Python Orchestrator
"""

import json
import os
import sys
import unittest
from unittest.mock import Mock, patch, MagicMock
from dataclasses import asdict

# Add the source directory to path for imports
sys.path.insert(0, 'app/src/main/python')

from orchestrator import (  # type: ignore
    LuminaOrchestrator,
    AIIntent,
    IntentAction,
    RenderMode,
    EffectType,
    DAGNode,
    initialize,
    parse_intent,
    shutdown,
    _test,
)
from classifiers import rule_based_classify, extract_intensity  # type: ignore
from graph.dag import validate_dag, DAGNode, execute_dag  # type: ignore
from config import OrchestratorConfig  # type: ignore
from intent_types import clamp_confidence  # type: ignore
from classifiers import normalize_llm_result, merge_classifications, unknown_classification  # type: ignore
from pipeline import classify_intent, extract_parameters, validate_intent, finalize_intent  # type: ignore
import cgi as cgi_shim  # type: ignore


class TestAIIntent(unittest.TestCase):
    """Tests for AIIntent dataclass"""

    def test_default_constructor(self):
        """Test default AIIntent values"""
        intent = AIIntent()
        self.assertEqual(intent.action, "")
        self.assertEqual(intent.target, "")
        self.assertEqual(intent.parameters, "")
        self.assertEqual(intent.confidence, 0.0)
        self.assertEqual(intent.timestamp, 0)

    def test_custom_constructor(self):
        """Test AIIntent with custom values"""
        intent = AIIntent(
            action="add_effect",
            target="blur",
            parameters='{"intensity": 0.5}',
            confidence=0.95,
            timestamp=1234567890
        )
        self.assertEqual(intent.action, "add_effect")
        self.assertEqual(intent.target, "blur")
        self.assertEqual(intent.confidence, 0.95)

    def test_to_dict(self):
        """Test AIIntent to_dict conversion"""
        intent = AIIntent(action="test", target="target", confidence=0.8)
        result = intent.to_dict()
        
        self.assertIsInstance(result, dict)
        self.assertEqual(result["action"], "test")
        self.assertEqual(result["target"], "target")
        self.assertEqual(result["confidence"], 0.8)

    def test_to_json(self):
        """Test AIIntent JSON serialization"""
        intent = AIIntent(
            action="set_render_mode",
            target="depth_map",
            parameters="{}",
            confidence=0.9,
            timestamp=1000
        )
        json_str = intent.to_json()
        
        # Parse and verify
        parsed = json.loads(json_str)
        self.assertEqual(parsed["action"], "set_render_mode")
        self.assertEqual(parsed["target"], "depth_map")
        self.assertEqual(parsed["confidence"], 0.9)


class TestEnums(unittest.TestCase):
    """Tests for enum definitions"""

    def test_intent_action_values(self):
        """Test IntentAction enum values"""
        self.assertEqual(IntentAction.SET_RENDER_MODE.value, "set_render_mode")
        self.assertEqual(IntentAction.ADD_EFFECT.value, "add_effect")
        self.assertEqual(IntentAction.REMOVE_EFFECT.value, "remove_effect")
        self.assertEqual(IntentAction.CAPTURE_FRAME.value, "capture_frame")
        self.assertEqual(IntentAction.RESET.value, "reset")
        self.assertEqual(IntentAction.HELP.value, "help")
        self.assertEqual(IntentAction.UNKNOWN.value, "unknown")

    def test_render_mode_values(self):
        """Test RenderMode enum values"""
        self.assertEqual(RenderMode.PASSTHROUGH.value, 0)
        self.assertEqual(RenderMode.STYLIZED.value, 1)
        self.assertEqual(RenderMode.SEGMENTED.value, 2)
        self.assertEqual(RenderMode.DEPTH_MAP.value, 3)
        self.assertEqual(RenderMode.NORMAL_MAP.value, 4)

    def test_effect_type_values(self):
        """Test EffectType enum values"""
        self.assertEqual(EffectType.NONE.value, 0)
        self.assertEqual(EffectType.BLUR.value, 1)
        self.assertEqual(EffectType.BLOOM.value, 2)
        self.assertEqual(EffectType.COLOR_GRADE.value, 3)
        self.assertEqual(EffectType.VIGNETTE.value, 4)
        self.assertEqual(EffectType.CHROMATIC_ABERRATION.value, 5)
        self.assertEqual(EffectType.NOISE.value, 6)
        self.assertEqual(EffectType.SHARPEN.value, 7)


class TestDAGNode(unittest.TestCase):
    """Tests for DAGNode dataclass"""

    def test_dag_node_creation(self):
        """Test DAGNode creation"""
        processor = lambda x, y: y
        node = DAGNode(name="test", processor=processor)
        
        self.assertEqual(node.name, "test")
        self.assertEqual(node.processor, processor)
        self.assertEqual(node.dependencies, [])

    def test_dag_node_with_dependencies(self):
        """Test DAGNode with dependencies"""
        processor = lambda x, y: y
        node = DAGNode(
            name="child",
            processor=processor,
            dependencies=["parent1", "parent2"]
        )
        
        self.assertEqual(node.dependencies, ["parent1", "parent2"])


class TestDAGValidation(unittest.TestCase):
    """Tests for DAG validation robustness"""

    def test_missing_node_in_order_raises(self):
        nodes = {
            "a": DAGNode(name="a", processor=lambda x, c: c, dependencies=[]),
        }
        with self.assertRaises(ValueError):
            validate_dag(nodes, ["a", "b"])

    def test_unscheduled_node_raises(self):
        nodes = {
            "a": DAGNode(name="a", processor=lambda x, c: c, dependencies=[]),
            "b": DAGNode(name="b", processor=lambda x, c: c, dependencies=["a"]),
        }
        with self.assertRaises(ValueError):
            validate_dag(nodes, ["a"])

    def test_dependency_order_violation_raises(self):
        nodes = {
            "a": DAGNode(name="a", processor=lambda x, c: c, dependencies=[]),
            "b": DAGNode(name="b", processor=lambda x, c: c, dependencies=["a"]),
        }
        with self.assertRaises(ValueError):
            validate_dag(nodes, ["b", "a"])


class TestDAGErrorHandling(unittest.TestCase):
    """Ensure orchestrator handles DAG errors defensively"""

    def test_dag_exception_returns_unknown(self):
        orch = LuminaOrchestrator()
        orch.initialize()

        def boom(_u, _c):
            raise RuntimeError("boom")

        orch.dag_nodes = {
            "preprocess": DAGNode(name="preprocess", processor=boom, dependencies=[])
        }
        orch.execution_order = ["preprocess"]

        intent = orch.parse_intent("add blur")
        self.assertEqual(intent.action, "unknown")
        self.assertLessEqual(intent.confidence, 0.1)
        self.assertIsNotNone(orch.last_context)
        orch.shutdown()


class TestLuminaOrchestrator(unittest.TestCase):
    """Tests for LuminaOrchestrator class"""

    def setUp(self):
        """Set up test orchestrator"""
        self.orchestrator = LuminaOrchestrator(assets_path="/test/path")

    def tearDown(self):
        """Clean up"""
        self.orchestrator.shutdown()

    def test_initialization(self):
        """Test orchestrator initialization"""
        self.assertFalse(self.orchestrator.initialized)
        self.assertEqual(self.orchestrator.assets_path, "/test/path")
        self.assertIsNone(self.orchestrator.model)

    def test_dag_structure(self):
        """Test that DAG is properly built"""
        self.assertIn("preprocess", self.orchestrator.dag_nodes)
        self.assertIn("classify", self.orchestrator.dag_nodes)
        self.assertIn("extract", self.orchestrator.dag_nodes)
        self.assertIn("validate", self.orchestrator.dag_nodes)
        self.assertIn("finalize", self.orchestrator.dag_nodes)

    def test_dag_dependencies(self):
        """Test DAG dependencies are correct"""
        nodes = self.orchestrator.dag_nodes
        
        self.assertEqual(nodes["preprocess"].dependencies, [])
        self.assertEqual(nodes["classify"].dependencies, ["preprocess"])
        self.assertEqual(nodes["extract"].dependencies, ["classify"])
        self.assertEqual(nodes["validate"].dependencies, ["extract"])
        self.assertEqual(nodes["finalize"].dependencies, ["validate"])

    def test_initialize_without_model(self):
        """Test initialization when model file doesn't exist"""
        result = self.orchestrator.initialize("/nonexistent/path")
        self.assertTrue(result)  # Should succeed in mock mode
        self.assertTrue(self.orchestrator.initialized)

    def test_preprocess_input(self):
        """Test input preprocessing"""
        context = {"timestamp": 1234}
        result = self.orchestrator._preprocess_input("  Please add BLUR  ", context)
        
        self.assertIn("normalized_input", result)
        self.assertEqual(result["normalized_input"], "add blur")
        self.assertEqual(result["original_input"], "  Please add BLUR  ")

    def test_preprocess_truncation_flag(self):
        """Ensure overly long input is truncated and flagged."""
        context = {"timestamp": 1234}
        long_text = "blur " * 300  # > 512 chars
        result = self.orchestrator._preprocess_input(long_text, context)
        self.assertTrue(result.get("truncated"))
        self.assertLessEqual(len(result["normalized_input"]), 512)

    def test_preprocess_removes_fillers(self):
        """Test that filler words are removed"""
        context = {"timestamp": 1234}
        
        inputs = [
            ("please add blur", "add blur"),
            ("can you make it dreamy", "make it dreamy"),
            ("could you show depth", "show depth"),
            ("i want to reset", "reset"),
            ("i'd like to capture", "capture"),
        ]
        
        for input_text, expected in inputs:
            result = self.orchestrator._preprocess_input(input_text, context)
            self.assertEqual(result["normalized_input"], expected)


class TestRuleBasedClassification(unittest.TestCase):
    """Tests for rule-based intent classification"""

    def setUp(self):
        """Set up test orchestrator"""
        self.orchestrator = LuminaOrchestrator()
        self.orchestrator.initialize()

    def tearDown(self):
        """Clean up"""
        self.orchestrator.shutdown()

    # Render mode tests
    def test_classify_passthrough(self):
        """Test passthrough render mode detection"""
        for keyword in ["normal", "passthrough", "original", "clear"]:
            result = self.orchestrator._rule_based_classify(keyword)
            result = rule_based_classify(keyword)
            self.assertEqual(result["action"], "set_render_mode")
            self.assertEqual(result["target"], "passthrough")

    def test_classify_stylized(self):
        """Test stylized render mode detection"""
        for keyword in ["stylize", "artistic", "style", "paint"]:
            result = self.orchestrator._rule_based_classify(keyword)
            result = rule_based_classify(keyword)
            self.assertEqual(result["action"], "set_render_mode")
            self.assertEqual(result["target"], "stylized")

    def test_classify_segmented(self):
        """Test segmented render mode detection"""
        for keyword in ["segment", "separate", "isolate", "mask"]:
            result = self.orchestrator._rule_based_classify(keyword)
            result = rule_based_classify(keyword)
            self.assertEqual(result["action"], "set_render_mode")
            self.assertEqual(result["target"], "segmented")

    def test_classify_depth_map(self):
        """Test depth map render mode detection"""
        for keyword in ["depth", "3d", "distance"]:
            result = self.orchestrator._rule_based_classify(keyword)
            result = rule_based_classify(keyword)
            self.assertEqual(result["action"], "set_render_mode")
            self.assertEqual(result["target"], "depth_map")

    # Effect tests
    def test_classify_blur_effect(self):
        """Test blur effect detection"""
        for keyword in ["blur", "soft", "smooth", "fuzzy"]:
            result = rule_based_classify(keyword)
            self.assertEqual(result["action"], "add_effect")
            self.assertEqual(result["target"], "blur")

    def test_classify_bloom_effect(self):
        """Test bloom effect detection"""
        for keyword in ["bloom", "glow", "dreamy", "ethereal"]:
            result = rule_based_classify(keyword)
            self.assertEqual(result["action"], "add_effect")
            self.assertEqual(result["target"], "bloom")

    def test_classify_vignette_effect(self):
        """Test vignette effect detection"""
        for keyword in ["vignette", "border", "frame"]:
            result = rule_based_classify(keyword)
            self.assertEqual(result["action"], "add_effect")
            self.assertEqual(result["target"], "vignette")

    def test_classify_remove_effect(self):
        """Test remove effect detection"""
        result = rule_based_classify("remove blur")
        self.assertEqual(result["action"], "remove_effect")
        self.assertEqual(result["target"], "blur")

        result = rule_based_classify("turn off bloom")
        self.assertEqual(result["action"], "remove_effect")
        self.assertEqual(result["target"], "bloom")

    # Control tests
    def test_classify_capture(self):
        """Test capture frame detection"""
        for keyword in ["capture", "screenshot", "photo", "snap"]:
            result = rule_based_classify(keyword)
            self.assertEqual(result["action"], "capture_frame")

    def test_classify_recording(self):
        """Test recording detection"""
        result = rule_based_classify("start recording")
        self.assertEqual(result["action"], "start_recording")

        result = rule_based_classify("stop")
        self.assertEqual(result["action"], "stop_recording")

    def test_classify_reset(self):
        """Test reset detection"""
        # Note: "clear" and some keywords may match render modes, test with unambiguous keywords
        result = rule_based_classify("reset everything")
        self.assertEqual(result["action"], "reset")
        
        result = rule_based_classify("go back to default settings")
        self.assertEqual(result["action"], "reset")

    def test_classify_help(self):
        """Test help detection"""
        for keyword in ["help", "what can", "how to"]:
            result = rule_based_classify(keyword)
            self.assertEqual(result["action"], "help")

    def test_classify_unknown(self):
        """Test unknown input"""
        result = rule_based_classify("xyzabc123")
        self.assertEqual(result["action"], "unknown")


class TestIntensityExtraction(unittest.TestCase):
    """Tests for intensity value extraction"""

    def setUp(self):
        """Set up test orchestrator"""
        self.orchestrator = LuminaOrchestrator()

    def test_extract_percentage(self):
        """Test percentage extraction"""
        self.assertEqual(extract_intensity("50%"), 0.5)
        self.assertEqual(extract_intensity("100%"), 1.0)
        self.assertEqual(extract_intensity("25 %"), 0.25)

    def test_extract_subtle(self):
        """Test subtle intensity detection"""
        for word in ["subtle", "light", "slight", "little"]:
            result = extract_intensity(f"add {word} blur")
            self.assertEqual(result, 0.3)

    def test_extract_medium(self):
        """Test medium intensity detection"""
        for word in ["medium", "moderate", "normal"]:
            result = extract_intensity(f"add {word} blur")
            self.assertEqual(result, 0.5)

    def test_extract_strong(self):
        for word in ["strong", "heavy", "intense", "max"]:
            result = extract_intensity(f"add {word} blur")
            self.assertEqual(result, 0.8)


class TestClassifierMerging(unittest.TestCase):
    def test_normalize_llm_result_bounds_and_params(self):
        self.assertIsNone(normalize_llm_result(None))
        self.assertIsNone(normalize_llm_result({}))

        res = normalize_llm_result({
            "action": "add_effect",
            "target": "blur",
            "parameters": "{\"intensity\": 0.7}",
            "confidence": 1.5,
        })
        self.assertEqual(res["parameters"], {"intensity": 0.7})
        self.assertLessEqual(res["confidence"], 1.0)

    def test_normalize_llm_result_bad_json_and_missing_action(self):
        self.assertIsNone(normalize_llm_result({
            "action": "",
            "target": "",
            "parameters": "{not-json}",
            "confidence": 0.2,
        }))

    def test_merge_prefers_valid_llm(self):
        llm = {
            "action": IntentAction.SET_RENDER_MODE.value,
            "target": "stylized",
            "parameters": {"foo": "bar"},
            "confidence": 0.95,
        }
        rule = rule_based_classify("blur")
        merged = merge_classifications(llm, rule)
        self.assertEqual(merged["source"], "llm")
        self.assertEqual(merged["target"], "stylized")

    def test_merge_rejects_invalid_llm(self):
        llm = {"action": "bad", "target": "n/a", "confidence": 0.99}
        rule = rule_based_classify("blur")
        merged = merge_classifications(llm, rule)
        self.assertEqual(merged["source"], "rule")

    def test_merge_add_effect_invalid_target(self):
        llm = {
            "action": IntentAction.ADD_EFFECT.value,
            "target": "notreal",
            "confidence": 0.9,
        }
        rule = rule_based_classify("blur")
        merged = merge_classifications(llm, rule)
        self.assertEqual(merged["target"], rule["target"])

    def test_unknown_classification(self):
        unk = unknown_classification("oops")
        self.assertEqual(unk["action"], IntentAction.UNKNOWN.value)
        self.assertEqual(unk["target"], "oops")


class TestClampConfidence(unittest.TestCase):
    def test_clamp_bounds_and_invalid(self):
        self.assertEqual(clamp_confidence(1.5), 1.0)
        self.assertEqual(clamp_confidence(-0.2), 0.0)
        self.assertEqual(clamp_confidence(object()), 0.0)


class TestPipelineExtras(unittest.TestCase):
    def test_classify_intent_llm_path_and_exception(self):
        class Dummy:
            def __init__(self, model, llm_response=None, raise_exc=False):
                self.model = model
                self.llm_response = llm_response
                self.raise_exc = raise_exc
                self.config = type("Cfg", (), {"rule_confidence_skip_llm": 0.95})

            def query_llm(self, normalized):
                if self.raise_exc:
                    raise RuntimeError("llm boom")
                return self.llm_response

        # Successful LLM merge
        orch_ok = Dummy(model=object(), llm_response={
            "action": IntentAction.SET_RENDER_MODE.value,
            "target": "stylized",
            "parameters": {},
            "confidence": 0.96,
        })
        ctx = classify_intent(orch_ok, "make it stylized", {})
        self.assertEqual(ctx["classification"].get("source"), "llm")

        # Exception path falls back to rule
        orch_fail = Dummy(model=object(), raise_exc=True)
        ctx2 = classify_intent(orch_fail, "blur please", {})
        self.assertEqual(ctx2["classification"].get("source"), "rule")

    def test_extract_parameters_handles_strings(self):
        context = {
            "classification": {
                "action": IntentAction.ADD_EFFECT.value,
                "target": "blur",
                "parameters": "{\"intensity\": 0.4}",
                "confidence": 0.8,
                "source": "llm",
            }
        }
        out = extract_parameters(None, "", context)
        self.assertEqual(out["parameters"], {"intensity": 0.4})
        self.assertEqual(out["action"], IntentAction.ADD_EFFECT.value)

        context_bad = {
            "classification": {
                "action": IntentAction.ADD_EFFECT.value,
                "target": "blur",
                "parameters": "not-json",
                "confidence": 0.8,
                "source": "llm",
            }
        }
        out_bad = extract_parameters(None, "", context_bad)
        self.assertEqual(out_bad["parameters"], {})

        context_list = {
            "classification": {
                "action": IntentAction.ADD_EFFECT.value,
                "target": "blur",
                "parameters": "[1,2,3]",
                "confidence": 0.7,
                "source": "llm",
            }
        }
        out_list = extract_parameters(None, "", context_list)
        self.assertEqual(out_list["parameters"], {})

    def test_validate_and_finalize(self):
        ctx = {
            "action": "invalid_action",
            "target": "",
            "confidence": 0.9,
        }
        validated = validate_intent(None, "", ctx)
        self.assertEqual(validated["action"], IntentAction.UNKNOWN.value)
        self.assertLessEqual(validated["confidence"], 0.3)
        self.assertTrue(validated["validated"])

        finalized = finalize_intent(None, "", validated)
        self.assertIsInstance(finalized["intent"], AIIntent)
        self.assertEqual(finalized["intent"].action, IntentAction.UNKNOWN.value)


class TestGraphExecute(unittest.TestCase):
    def test_execute_dag_telemetry(self):
        nodes = {
            "a": DAGNode(name="a", processor=lambda u, c: c),
        }
        order = ["a"]
        ctx = execute_dag(nodes, order, "input", {})
        self.assertIn("a", ctx["telemetry"]["nodes"])

    def test_validate_dag_missing_dependency(self):
        nodes = {
            "a": DAGNode(name="a", processor=lambda u, c: c, dependencies=["b"])
        }
        with self.assertRaises(ValueError):
            validate_dag(nodes, ["a"])


class TestOrchestratorExtras(unittest.TestCase):
    def test_history_and_unknown_on_exception(self):
        orch = LuminaOrchestrator()
        orch.max_history = 2
        orch._add_to_history(AIIntent(action="one"))
        orch._add_to_history(AIIntent(action="two"))
        orch._add_to_history(AIIntent(action="three"))
        self.assertEqual(len(orch.intent_history), 2)
        self.assertEqual(len(orch.get_history()), 2)

        with patch("orchestrator.execute_dag", side_effect=RuntimeError("boom")):
            intent = orch.parse_intent("fail")
            self.assertEqual(intent.action, IntentAction.UNKNOWN.value)
            self.assertIsNotNone(orch.last_context.get("error"))

    def test_query_llm_none_model(self):
        orch = LuminaOrchestrator()
        orch.model = None
        self.assertIsNone(orch.query_llm("hello"))

    def test_global_helpers(self):
        # Ensure global orchestrator lifecycle works in mock mode
        self.assertTrue(initialize("/tmp"))
        json_str = parse_intent("add blur")
        parsed = json.loads(json_str)
        self.assertIn("action", parsed)
        shutdown()


class TestOrchestratorBranches(unittest.TestCase):
    def test_initialize_sets_grammar_path(self):
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            grammar = os.path.join(tmp, "qwen_grammar.gbnf")
            with open(grammar, "w", encoding="utf-8") as f:
                f.write("grammar")
            orch = LuminaOrchestrator()
            orch.initialize(tmp)
            self.assertEqual(orch.grammar_path, grammar)

    def test_initialize_model_missing_when_llama_available(self):
        with patch("orchestrator.LLAMA_AVAILABLE", True), patch("orchestrator.Llama", lambda **kwargs: "model"), patch("orchestrator.os.path.exists", return_value=False):
            orch = LuminaOrchestrator("/tmp")
            orch.initialize()
            self.assertTrue(orch.initialized)

    def test_initialize_model_loads_when_present(self):
        class DummyModel:
            def __init__(self, **_kwargs):
                pass

            def __call__(self, *args, **kwargs):
                return {"choices": [{"text": "{}"}]}

        with patch("orchestrator.LLAMA_AVAILABLE", True), patch("orchestrator.Llama", DummyModel), patch("orchestrator.os.path.exists", return_value=True):
            orch = LuminaOrchestrator("/tmp")
            orch.initialize()
            self.assertIsNotNone(orch.model)

    def test_query_llm_parses_json(self):
        class Dummy:
            def __call__(self, *args, **kwargs):
                return {"choices": [{"text": " {\"action\":\"add_effect\",\"target\":\"blur\"} "}]}

        orch = LuminaOrchestrator()
        orch.model = Dummy()
        result = orch.query_llm("hi")
        self.assertEqual(result["action"], "add_effect")

    def test_query_llm_legacy_method(self):
        class Dummy:
            def __call__(self, *args, **kwargs):
                return {"choices": [{"text": " {\"action\":\"set_render_mode\"} "}]}

        orch = LuminaOrchestrator()
        orch.model = Dummy()
        result = orch._query_llm("hi")
        self.assertEqual(result["action"], "set_render_mode")

    def test_test_function_runs(self):
        # Should run in mock mode without raising
        _test()


class TestCgiShim(unittest.TestCase):
    def test_parse_header_and_exports(self):
        key, pdict = cgi_shim.parse_header('text/plain; charset="utf-8"; boundary=abc')
        self.assertEqual(key, "text/plain")
        self.assertEqual(pdict["charset"], "utf-8")
        self.assertEqual(pdict["boundary"], "abc")
        self.assertTrue(hasattr(cgi_shim, "FieldStorage"))
        self.assertEqual(cgi_shim._parseparam("x"), "x")

    def test_extract_strong(self):
        """Test strong intensity detection"""
        for word in ["strong", "heavy", "intense", "max"]:
            result = extract_intensity(f"add {word} blur")
            self.assertEqual(result, 0.8)

    def test_extract_none(self):
        """Test when no intensity is specified"""
        result = extract_intensity("add blur")
        self.assertIsNone(result)


class TestIntentValidation(unittest.TestCase):
    """Tests for intent validation"""

    def setUp(self):
        """Set up test orchestrator"""
        self.orchestrator = LuminaOrchestrator()
        self.orchestrator.initialize()

    def tearDown(self):
        """Clean up"""
        self.orchestrator.shutdown()

    def test_validate_known_action(self):
        """Test validation of known action"""
        context = {
            "action": "add_effect",
            "target": "blur",
            "confidence": 0.8
        }
        result = self.orchestrator._validate_intent("add blur", context)
        
        self.assertTrue(result.get("validated"))
        self.assertEqual(result["action"], "add_effect")
        self.assertEqual(result["confidence"], 0.8)

    def test_validate_unknown_action(self):
        """Test validation of unknown action"""
        context = {
            "action": "invalid_action",
            "target": "something",
            "confidence": 0.8
        }
        result = self.orchestrator._validate_intent("test", context)
        
        self.assertEqual(result["action"], "unknown")
        self.assertLessEqual(result["confidence"], 0.3)

    def test_validate_invalid_render_mode(self):
        """Test validation of invalid render mode target"""
        context = {
            "action": "set_render_mode",
            "target": "invalid_mode",
            "confidence": 0.8
        }
        result = self.orchestrator._validate_intent("test", context)
        
        self.assertLessEqual(result["confidence"], 0.4)

    def test_validate_invalid_effect(self):
        """Test validation of invalid effect target"""
        context = {
            "action": "add_effect",
            "target": "invalid_effect",
            "confidence": 0.8
        }
        result = self.orchestrator._validate_intent("test", context)
        
        self.assertLessEqual(result["confidence"], 0.4)


class TestFullPipeline(unittest.TestCase):
    """Integration tests for the full intent parsing pipeline"""

    def setUp(self):
        """Set up test orchestrator"""
        self.orchestrator = LuminaOrchestrator()
        self.orchestrator.initialize()

    def tearDown(self):
        """Clean up"""
        self.orchestrator.shutdown()

    def test_parse_intent_blur(self):
        """Test full pipeline for blur effect"""
        intent = self.orchestrator.parse_intent("add blur effect")
        
        self.assertEqual(intent.action, "add_effect")
        self.assertEqual(intent.target, "blur")
        self.assertGreater(intent.confidence, 0)
        self.assertGreater(intent.timestamp, 0)

    def test_parse_intent_dreamy(self):
        """Test full pipeline for dreamy/bloom effect"""
        intent = self.orchestrator.parse_intent("make it look dreamy")
        
        self.assertEqual(intent.action, "add_effect")
        self.assertEqual(intent.target, "bloom")

    def test_parse_intent_depth(self):
        """Test full pipeline for depth map"""
        intent = self.orchestrator.parse_intent("show me the depth")
        
        self.assertEqual(intent.action, "set_render_mode")
        self.assertEqual(intent.target, "depth_map")

    def test_parse_intent_with_intensity(self):
        """Test full pipeline with intensity"""
        intent = self.orchestrator.parse_intent("add subtle blur")
        
        self.assertEqual(intent.action, "add_effect")
        self.assertEqual(intent.target, "blur")
        
        # Parse parameters
        params = json.loads(intent.parameters)
        self.assertEqual(params.get("intensity"), 0.3)

    def test_last_context_recorded(self):
        """Ensure last_context is populated after parse."""
        _ = self.orchestrator.parse_intent("add blur")
        self.assertIsNotNone(self.orchestrator.last_context)
        self.assertIn("telemetry", self.orchestrator.last_context)

    def test_configurable_threshold_skips_llm(self):
        """Lower threshold should still allow LLM skip when above threshold."""
        cfg = OrchestratorConfig(rule_confidence_skip_llm=0.8)
        orch = LuminaOrchestrator(config=cfg)
        orch.initialize()
        with patch.object(orch, "query_llm", autospec=True) as mock_llm:
            _ = orch.parse_intent("help")  # rule confidence 0.95
            mock_llm.assert_not_called()
        orch.shutdown()

    def test_parse_intent_includes_telemetry(self):
        """Telemetry should capture per-node timing."""
        _ = self.orchestrator.parse_intent("add blur")
        telemetry = getattr(self.orchestrator, "last_context", None)
        if telemetry is None:
            # Fallback: re-run and grab context via internal parse
            context = {"input": "add blur", "timestamp": 0}
            context = self.orchestrator.dag_nodes["preprocess"].processor("add blur", context)
        else:
            context = telemetry
        # To keep it simple, run a fresh parse and inspect the returned context
        context = {"input": "add blur", "timestamp": 0}
        from graph.dag import execute_dag  # type: ignore
        context = execute_dag(self.orchestrator.dag_nodes, self.orchestrator.execution_order, "add blur", context)
        self.assertIn("telemetry", context)
        self.assertIn("nodes", context["telemetry"])
        self.assertTrue(all(k in context["telemetry"]["nodes"] for k in self.orchestrator.execution_order))

    def test_parse_intent_reset(self):
        """Test full pipeline for reset"""
        intent = self.orchestrator.parse_intent("reset everything")
        
        self.assertEqual(intent.action, "reset")

    def test_parse_intent_history(self):
        """Test that intents are added to history"""
        self.orchestrator.parse_intent("add blur")
        self.orchestrator.parse_intent("show depth")
        
        history = self.orchestrator.get_history()
        self.assertEqual(len(history), 2)
        self.assertEqual(history[0]["action"], "add_effect")
        self.assertEqual(history[1]["action"], "set_render_mode")

    def test_history_limit(self):
        """Test that history is limited"""
        for i in range(15):
            self.orchestrator.parse_intent(f"test {i}")
        
        history = self.orchestrator.get_history()
        self.assertLessEqual(len(history), 10)


class TestHeuristicMerging(unittest.TestCase):
    """Tests for LLM/rule merging heuristics and guardrails"""

    def setUp(self):
        self.orchestrator = LuminaOrchestrator()
        self.orchestrator.initialize()
        # Force LLM path to execute by providing a mock model object
        self.orchestrator.model = object()

    def tearDown(self):
        self.orchestrator.shutdown()

    def test_llm_low_confidence_prefers_rule(self):
        """Low-confidence LLM output should defer to rule classification."""
        with patch.object(self.orchestrator, "_query_llm", return_value={
            "action": "add_effect",
            "target": "noise",
            "confidence": 0.2
        }):
            intent = self.orchestrator.parse_intent("add blur")
            self.assertEqual(intent.target, "blur")
            self.assertEqual(intent.action, "add_effect")

    def test_llm_invalid_result_falls_back_to_rule(self):
        """Malformed LLM outputs should fall back to the rule path."""
        with patch.object(self.orchestrator, "_query_llm", return_value={
            "action": "invalid_action",
            "target": "???",
            "confidence": 0.9
        }):
            intent = self.orchestrator.parse_intent("add blur")
            self.assertEqual(intent.target, "blur")
            self.assertEqual(intent.action, "add_effect")

    def test_llm_parameters_non_json_are_sanitized(self):
        """Non-JSON parameters from LLM should not break parsing."""
        with patch.object(self.orchestrator, "_query_llm", return_value={
            "action": "add_effect",
            "target": "blur",
            "parameters": "not-json",
            "confidence": 0.9
        }):
            intent = self.orchestrator.parse_intent("add blur")
            params = json.loads(intent.parameters)
            self.assertEqual(params, {})

    def test_empty_input_sets_low_confidence_unknown(self):
        """Empty input is guarded and marked unknown with low confidence."""
        intent = self.orchestrator.parse_intent("")
        self.assertEqual(intent.action, "unknown")
        self.assertLessEqual(intent.confidence, 0.2)

    def test_high_conf_rule_skips_llm(self):
        """High-confidence rule classification should skip LLM calls for efficiency."""
        with patch.object(self.orchestrator, "query_llm", autospec=True) as mock_llm:
            intent = self.orchestrator.parse_intent("help")  # rule confidence 0.95
            self.assertEqual(intent.action, "help")
            mock_llm.assert_not_called()


class TestGlobalFunctions(unittest.TestCase):
    """Tests for global module functions"""

    def test_initialize_function(self):
        """Test global initialize function"""
        result = initialize("/test/path")
        self.assertTrue(result)
        shutdown()

    def test_parse_intent_function(self):
        """Test global parse_intent function"""
        result = parse_intent("add blur")
        
        # Should return valid JSON
        parsed = json.loads(result)
        self.assertIn("action", parsed)
        self.assertIn("target", parsed)
        self.assertIn("confidence", parsed)
        
        shutdown()

    def test_shutdown_function(self):
        """Test global shutdown function"""
        initialize("/test")
        shutdown()
        # Should not raise
        shutdown()  # Second call should be safe


class TestEdgeCases(unittest.TestCase):
    """Tests for edge cases and error handling"""

    def setUp(self):
        """Set up test orchestrator"""
        self.orchestrator = LuminaOrchestrator()
        self.orchestrator.initialize()

    def tearDown(self):
        """Clean up"""
        self.orchestrator.shutdown()

    def test_empty_input(self):
        """Test empty input handling"""
        intent = self.orchestrator.parse_intent("")
        self.assertEqual(intent.action, "unknown")

    def test_whitespace_input(self):
        """Test whitespace-only input"""
        intent = self.orchestrator.parse_intent("   ")
        self.assertEqual(intent.action, "unknown")

    def test_very_long_input(self):
        """Test very long input"""
        long_input = "blur " * 1000
        intent = self.orchestrator.parse_intent(long_input)
        # Should still work
        self.assertIsNotNone(intent.action)

    def test_special_characters(self):
        """Test input with special characters"""
        intent = self.orchestrator.parse_intent("add blur!@#$%")
        self.assertEqual(intent.action, "add_effect")
        self.assertEqual(intent.target, "blur")

    def test_unicode_input(self):
        """Test unicode input"""
        intent = self.orchestrator.parse_intent("add blur 日本語")
        # Should handle gracefully
        self.assertIsNotNone(intent.action)

    def test_case_insensitivity(self):
        """Test case insensitivity"""
        lower = self.orchestrator.parse_intent("add blur")
        upper = self.orchestrator.parse_intent("ADD BLUR")
        mixed = self.orchestrator.parse_intent("Add BLUR")
        
        self.assertEqual(lower.action, upper.action)
        self.assertEqual(lower.action, mixed.action)
        self.assertEqual(lower.target, upper.target)


if __name__ == '__main__':
    unittest.main()
