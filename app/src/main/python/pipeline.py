"""Pipeline node processors for the Lumina micro-DAG."""
import json
import time
from typing import Dict

from intent_types import AIIntent, IntentAction, RenderMode, EffectType, clamp_confidence
from classifiers import (
    rule_based_classify,
    normalize_llm_result,
    merge_classifications,
    unknown_classification,
)


def preprocess_input(orch, user_input: str, context: Dict) -> Dict:
    """Preprocess and normalize user input with length guardrails."""
    normalized = user_input.lower().strip()
    fillers = ["please", "can you", "could you", "i want to", "i'd like to"]
    for filler in fillers:
        normalized = normalized.replace(filler, "").strip()

    max_len = getattr(getattr(orch, "config", None), "max_normalized_length", 512)
    if len(normalized) > max_len:
        normalized = normalized[:max_len]
        context["truncated"] = True

    context["normalized_input"] = normalized
    context["original_input"] = user_input
    return context


def classify_intent(orch, user_input: str, context: Dict) -> Dict:
    """Classify the intent using LLM + rule fallback with merging."""
    normalized = context.get("normalized_input", user_input.lower().strip())

    if not normalized:
        classification = unknown_classification("empty_input")
        context["classification"] = classification
        context["confidence"] = classification["confidence"]
        return context

    rule_result = rule_based_classify(normalized)
    llm_result = None

    skip_threshold = getattr(getattr(orch, "config", None), "rule_confidence_skip_llm", 0.9)

    # If rule-based is already high-confidence, skip LLM to save resources
    if rule_result.get("confidence", 0.0) < skip_threshold and getattr(orch, "model", None) is not None:
        try:
            llm_raw = orch.query_llm(normalized)
            llm_result = normalize_llm_result(llm_raw)
            if llm_result:
                context["llm_result"] = llm_result
        except Exception as e:
            print(f"LLM query failed: {e}")

    classification = merge_classifications(llm_result, rule_result)

    context["classification"] = classification
    context["rule_result"] = rule_result
    context["confidence"] = classification.get("confidence", 0.0)
    return context


def extract_parameters(_orch, _user_input: str, context: Dict) -> Dict:
    """Extract detailed parameters from the merged classification."""
    classification = context.get("classification")
    if classification:
        params = classification.get("parameters", {})
        if not isinstance(params, dict):
            try:
                params = json.loads(params)
                if not isinstance(params, dict):
                    params = {}
            except Exception:
                params = {}

        context["action"] = classification.get("action", IntentAction.UNKNOWN.value)
        context["target"] = classification.get("target", "")
        context["parameters"] = params
        context["confidence"] = classification.get("confidence", 0.0)
        context["classification_source"] = classification.get("source")

    return context


def validate_intent(_orch, _user_input: str, context: Dict) -> Dict:
    """Validate the extracted intent."""
    action = context.get("action", IntentAction.UNKNOWN.value)
    target = context.get("target", "")

    valid_actions = [a.value for a in IntentAction]
    if action not in valid_actions:
        context["action"] = IntentAction.UNKNOWN.value
        context["confidence"] = min(context.get("confidence", 0.5), 0.3)

    if action == IntentAction.SET_RENDER_MODE.value:
        valid_modes = [m.name.lower() for m in RenderMode]
        if target.lower() not in valid_modes:
            context["confidence"] = min(context.get("confidence", 0.5), 0.4)

    if action in [IntentAction.ADD_EFFECT.value, IntentAction.REMOVE_EFFECT.value]:
        valid_effects = [e.name.lower() for e in EffectType]
        if target.lower() not in valid_effects:
            context["confidence"] = min(context.get("confidence", 0.5), 0.4)

    context["confidence"] = clamp_confidence(context.get("confidence", 0.0))
    context["validated"] = True
    return context


def finalize_intent(_orch, _user_input: str, context: Dict) -> Dict:
    """Finalize and create the AIIntent object."""
    params = context.get("parameters", {})
    params_json = json.dumps(params) if params else "{}"

    intent = AIIntent(
        action=context.get("action", IntentAction.UNKNOWN.value),
        target=context.get("target", ""),
        parameters=params_json,
        confidence=context.get("confidence", 0.0),
        timestamp=context.get("timestamp", int(time.time() * 1000)),
    )

    context["intent"] = intent
    return context


__all__ = [
    "preprocess_input",
    "classify_intent",
    "extract_parameters",
    "validate_intent",
    "finalize_intent",
]
