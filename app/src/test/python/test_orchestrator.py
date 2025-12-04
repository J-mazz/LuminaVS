"""
Unit tests for Lumina Virtual Studio - Python Orchestrator
"""

import json
import sys
import unittest
from unittest.mock import Mock, patch, MagicMock
from dataclasses import asdict

# Add the source directory to path for imports
sys.path.insert(0, 'app/src/main/python')

from orchestrator import (
    LuminaOrchestrator,
    AIIntent,
    IntentAction,
    RenderMode,
    EffectType,
    DAGNode,
    initialize,
    parse_intent,
    shutdown
)


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
            self.assertEqual(result["action"], "set_render_mode")
            self.assertEqual(result["target"], "passthrough")

    def test_classify_stylized(self):
        """Test stylized render mode detection"""
        for keyword in ["stylize", "artistic", "style", "paint"]:
            result = self.orchestrator._rule_based_classify(keyword)
            self.assertEqual(result["action"], "set_render_mode")
            self.assertEqual(result["target"], "stylized")

    def test_classify_segmented(self):
        """Test segmented render mode detection"""
        for keyword in ["segment", "separate", "isolate", "mask"]:
            result = self.orchestrator._rule_based_classify(keyword)
            self.assertEqual(result["action"], "set_render_mode")
            self.assertEqual(result["target"], "segmented")

    def test_classify_depth_map(self):
        """Test depth map render mode detection"""
        for keyword in ["depth", "3d", "distance"]:
            result = self.orchestrator._rule_based_classify(keyword)
            self.assertEqual(result["action"], "set_render_mode")
            self.assertEqual(result["target"], "depth_map")

    # Effect tests
    def test_classify_blur_effect(self):
        """Test blur effect detection"""
        for keyword in ["blur", "soft", "smooth", "fuzzy"]:
            result = self.orchestrator._rule_based_classify(keyword)
            self.assertEqual(result["action"], "add_effect")
            self.assertEqual(result["target"], "blur")

    def test_classify_bloom_effect(self):
        """Test bloom effect detection"""
        for keyword in ["bloom", "glow", "dreamy", "ethereal"]:
            result = self.orchestrator._rule_based_classify(keyword)
            self.assertEqual(result["action"], "add_effect")
            self.assertEqual(result["target"], "bloom")

    def test_classify_vignette_effect(self):
        """Test vignette effect detection"""
        for keyword in ["vignette", "border", "frame"]:
            result = self.orchestrator._rule_based_classify(keyword)
            self.assertEqual(result["action"], "add_effect")
            self.assertEqual(result["target"], "vignette")

    def test_classify_remove_effect(self):
        """Test remove effect detection"""
        result = self.orchestrator._rule_based_classify("remove blur")
        self.assertEqual(result["action"], "remove_effect")
        self.assertEqual(result["target"], "blur")

        result = self.orchestrator._rule_based_classify("turn off bloom")
        self.assertEqual(result["action"], "remove_effect")
        self.assertEqual(result["target"], "bloom")

    # Control tests
    def test_classify_capture(self):
        """Test capture frame detection"""
        for keyword in ["capture", "screenshot", "photo", "snap"]:
            result = self.orchestrator._rule_based_classify(keyword)
            self.assertEqual(result["action"], "capture_frame")

    def test_classify_recording(self):
        """Test recording detection"""
        result = self.orchestrator._rule_based_classify("start recording")
        self.assertEqual(result["action"], "start_recording")

        result = self.orchestrator._rule_based_classify("stop")
        self.assertEqual(result["action"], "stop_recording")

    def test_classify_reset(self):
        """Test reset detection"""
        # Note: "clear" and some keywords may match render modes, test with unambiguous keywords
        result = self.orchestrator._rule_based_classify("reset everything")
        self.assertEqual(result["action"], "reset")
        
        result = self.orchestrator._rule_based_classify("go back to default settings")
        self.assertEqual(result["action"], "reset")

    def test_classify_help(self):
        """Test help detection"""
        for keyword in ["help", "what can", "how to"]:
            result = self.orchestrator._rule_based_classify(keyword)
            self.assertEqual(result["action"], "help")

    def test_classify_unknown(self):
        """Test unknown input"""
        result = self.orchestrator._rule_based_classify("xyzabc123")
        self.assertEqual(result["action"], "unknown")


class TestIntensityExtraction(unittest.TestCase):
    """Tests for intensity value extraction"""

    def setUp(self):
        """Set up test orchestrator"""
        self.orchestrator = LuminaOrchestrator()

    def test_extract_percentage(self):
        """Test percentage extraction"""
        self.assertEqual(self.orchestrator._extract_intensity("50%"), 0.5)
        self.assertEqual(self.orchestrator._extract_intensity("100%"), 1.0)
        self.assertEqual(self.orchestrator._extract_intensity("25 %"), 0.25)

    def test_extract_subtle(self):
        """Test subtle intensity detection"""
        for word in ["subtle", "light", "slight", "little"]:
            result = self.orchestrator._extract_intensity(f"add {word} blur")
            self.assertEqual(result, 0.3)

    def test_extract_medium(self):
        """Test medium intensity detection"""
        for word in ["medium", "moderate", "normal"]:
            result = self.orchestrator._extract_intensity(f"add {word} blur")
            self.assertEqual(result, 0.5)

    def test_extract_strong(self):
        """Test strong intensity detection"""
        for word in ["strong", "heavy", "intense", "max"]:
            result = self.orchestrator._extract_intensity(f"add {word} blur")
            self.assertEqual(result, 0.8)

    def test_extract_none(self):
        """Test when no intensity is specified"""
        result = self.orchestrator._extract_intensity("add blur")
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
