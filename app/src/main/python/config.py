"""Configuration for the orchestrator routing and resource caps."""
from dataclasses import dataclass


@dataclass
class OrchestratorConfig:
    # Threshold above which rule-based classification is considered final (skip LLM)
    rule_confidence_skip_llm: float = 0.9

    # Maximum tokens to request from the LLM (for mobile efficiency)
    max_llm_tokens: int = 96

    # Maximum normalized input length before truncation
    max_normalized_length: int = 512

    # Default effect intensity when user requests intensity without a number
    default_effect_intensity: float = 0.5

    # Enable telemetry timings per node
    telemetry_enabled: bool = True


__all__ = ["OrchestratorConfig"]