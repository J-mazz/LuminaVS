"""Micro-DAG node definitions and execution helpers."""
import time
from dataclasses import dataclass
from typing import Callable, Dict, List


@dataclass
class DAGNode:
    """Node in the Micro-DAG processing pipeline"""
    name: str
    processor: Callable[[str, Dict], Dict]
    dependencies: List[str] = None

    def __post_init__(self):
        if self.dependencies is None:
            self.dependencies = []


def execute_dag(nodes: Dict[str, "DAGNode"], order: List[str], user_input: str, context: Dict) -> Dict:
    """Execute DAG nodes in the given order with validation and dependency checks.

    Adds lightweight telemetry timings per node to the context under `telemetry`.
    """
    validate_dag(nodes, order)

    telemetry = context.setdefault("telemetry", {"nodes": {}})

    for node_name in order:
        node = nodes[node_name]
        start = time.perf_counter()
        context = node.processor(user_input, context)
        elapsed_ms = (time.perf_counter() - start) * 1000.0
        telemetry["nodes"][node_name] = {"ms": round(elapsed_ms, 3)}
    return context


def validate_dag(nodes: Dict[str, "DAGNode"], order: List[str]) -> None:
    """Validate DAG structure: missing nodes, unknown nodes, and dependency ordering."""
    missing = [name for name in order if name not in nodes]
    if missing:
        raise ValueError(f"Execution order references missing nodes: {missing}")

    unknown = [name for name in nodes.keys() if name not in order]
    if unknown:
        raise ValueError(f"DAG nodes not scheduled in execution order: {unknown}")

    position = {name: idx for idx, name in enumerate(order)}
    for name, node in nodes.items():
        for dep in node.dependencies:
            if dep not in position:
                raise ValueError(f"Dependency {dep} for node {name} not in execution order")
            if position[dep] > position[name]:
                raise ValueError(f"Dependency order violation: {dep} must run before {name}")
