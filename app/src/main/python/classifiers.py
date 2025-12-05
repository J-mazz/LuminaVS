"""Classification and merging utilities for the Lumina orchestrator."""
import json
from typing import Dict, Optional

from intent_types import IntentAction, RenderMode, EffectType, clamp_confidence


RENDER_MODES = {
    "passthrough": ["normal", "passthrough", "original", "clear"],
    "stylized": ["stylize", "artistic", "style", "paint"],
    "segmented": ["segment", "separate", "isolate", "mask"],
    "depth_map": ["depth", "3d", "distance"],
    "normal_map": ["normal", "surface", "bumps"],
}

EFFECTS = {
    "blur": ["blur", "soft", "smooth", "fuzzy"],
    "bloom": ["bloom", "glow", "dreamy", "ethereal"],
    "color_grade": ["color", "grade", "tint", "warm", "cool"],
    "vignette": ["vignette", "border", "frame", "dark edges"],
    "chromatic_aberration": ["chromatic", "rgb split", "glitch"],
    "noise": ["noise", "grain", "film", "vintage"],
    "sharpen": ["sharpen", "crisp", "detail", "enhance"],
}


def rule_based_classify(normalized: str) -> Dict:
    """Rule-based intent classification fallback"""
    result = {
        "action": IntentAction.UNKNOWN.value,
        "target": "",
        "parameters": {},
        "confidence": 0.5,
        "source": "rule",
    }

    for mode, keywords in RENDER_MODES.items():
        if any(kw in normalized for kw in keywords):
            result["action"] = IntentAction.SET_RENDER_MODE.value
            result["target"] = mode
            result["confidence"] = 0.75
            return result

    for effect, keywords in EFFECTS.items():
        if any(kw in normalized for kw in keywords):
            if "remove" in normalized or "off" in normalized:
                result["action"] = IntentAction.REMOVE_EFFECT.value
            else:
                result["action"] = IntentAction.ADD_EFFECT.value
            result["target"] = effect
            result["confidence"] = 0.7

            intensity = extract_intensity(normalized)
            if intensity is not None:
                result["parameters"] = {"intensity": intensity}
            return result

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


def extract_intensity(text: str) -> Optional[float]:
    """Extract intensity value from text"""
    import re

    match = re.search(r"(\d+)\s*%", text)
    if match:
        return int(match.group(1)) / 100.0

    if any(w in text for w in ["subtle", "light", "slight", "little"]):
        return 0.3
    if any(w in text for w in ["medium", "moderate", "normal"]):
        return 0.5
    if any(w in text for w in ["strong", "heavy", "intense", "max"]):
        return 0.8

    return None


def normalize_llm_result(raw: Optional[Dict]) -> Optional[Dict]:
    """Normalize LLM output and guard against malformed JSON."""
    if not raw or not isinstance(raw, dict):
        return None

    action = str(raw.get("action", "")).strip()
    target = str(raw.get("target", "")).strip().lower()

    params = raw.get("parameters", {})
    if isinstance(params, str):
        try:
            params = json.loads(params)
        except Exception:
            params = {}

    confidence = clamp_confidence(raw.get("confidence", 0.5))

    if not action:
        return None

    return {
        "action": action,
        "target": target,
        "parameters": params if isinstance(params, dict) else {},
        "confidence": confidence,
        "source": "llm",
    }


def merge_classifications(llm_result: Optional[Dict], rule_result: Dict) -> Dict:
    """Merge LLM and rule-based results with safety heuristics."""
    valid_actions = {a.value for a in IntentAction}
    classification = rule_result.copy()

    if llm_result:
        action_valid = llm_result.get("action") in valid_actions
        target_valid = True
        target_value = llm_result.get("target", "").lower()

        if llm_result.get("action") == IntentAction.SET_RENDER_MODE.value:
            target_valid = target_value in {m.name.lower() for m in RenderMode}
        if llm_result.get("action") in (
            IntentAction.ADD_EFFECT.value,
            IntentAction.REMOVE_EFFECT.value,
        ):
            target_valid = target_value in {e.name.lower() for e in EffectType}

        llm_conf = llm_result.get("confidence", 0.0)
        rule_conf = rule_result.get("confidence", 0.0)

        use_llm = action_valid and target_valid and llm_conf >= max(0.55, rule_conf - 0.05)

        if use_llm:
            classification = {
                "action": llm_result.get("action", IntentAction.UNKNOWN.value),
                "target": target_value,
                "parameters": llm_result.get("parameters", {}),
                "confidence": clamp_confidence(llm_conf),
                "source": "llm",
            }

    classification["confidence"] = clamp_confidence(classification.get("confidence", 0.0))
    return classification


def unknown_classification(reason: str) -> Dict:
    """Create a standardized unknown classification bucket."""
    return {
        "action": IntentAction.UNKNOWN.value,
        "target": reason,
        "parameters": {},
        "confidence": 0.2,
        "source": "guardrail",
    }


__all__ = [
    "rule_based_classify",
    "extract_intensity",
    "normalize_llm_result",
    "merge_classifications",
    "unknown_classification",
]
