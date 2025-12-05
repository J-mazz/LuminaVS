"""Shared intent types and helpers for the Lumina orchestrator."""
import json
from dataclasses import dataclass, asdict
from enum import Enum
from typing import Any, Dict


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


def clamp_confidence(value: float) -> float:
    """Clamp confidence into [0.0, 1.0]."""
    try:
        return max(0.0, min(1.0, float(value)))
    except Exception:
        return 0.0


__all__ = [
    "IntentAction",
    "RenderMode",
    "EffectType",
    "AIIntent",
    "clamp_confidence",
]
