"""Prometheus collectors for provider, graph, tool, and output boundaries."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from prometheus_client import CollectorRegistry, Counter, Histogram, REGISTRY


LATENCY_BUCKETS = (0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10, 30)


class AIMetrics:
    """Own the bounded-label collector schema used by the AI service."""

    LABEL_SCHEMA = {
        "ai_requests_total": ("provider", "model", "outcome"),
        "llm_latency_seconds": ("provider", "model", "outcome"),
        "agent_steps_total": ("node", "outcome"),
        "tool_calls_total": ("tool", "outcome"),
        "tool_latency_seconds": ("tool", "outcome"),
        "output_validation_total": ("result",),
        "mock_fallback_total": ("provider",),
        "agent_limit_total": ("limit",),
        "llm_tokens_total": ("provider", "model", "token_type"),
        "llm_cost_usd_total": ("provider", "model"),
    }

    def __init__(self, registry: CollectorRegistry = REGISTRY) -> None:
        self.registry = registry
        self.ai_requests = Counter(
            "ai_requests_total",
            "Completed AI recommendation requests.",
            self.LABEL_SCHEMA["ai_requests_total"],
            registry=registry,
        )
        self.llm_latency = Histogram(
            "llm_latency_seconds",
            "Latency of individual LLM calls.",
            self.LABEL_SCHEMA["llm_latency_seconds"],
            buckets=LATENCY_BUCKETS,
            registry=registry,
        )
        self.agent_steps = Counter(
            "agent_steps_total",
            "Completed LangGraph node executions.",
            self.LABEL_SCHEMA["agent_steps_total"],
            registry=registry,
        )
        self.tool_calls = Counter(
            "tool_calls_total",
            "Completed agent tool calls.",
            self.LABEL_SCHEMA["tool_calls_total"],
            registry=registry,
        )
        self.tool_latency = Histogram(
            "tool_latency_seconds",
            "Latency of agent tool calls.",
            self.LABEL_SCHEMA["tool_latency_seconds"],
            buckets=LATENCY_BUCKETS,
            registry=registry,
        )
        self.output_validation = Counter(
            "output_validation_total",
            "Final output guard decisions.",
            self.LABEL_SCHEMA["output_validation_total"],
            registry=registry,
        )
        self.mock_fallback = Counter(
            "mock_fallback_total",
            "Explicit development mock fallback responses.",
            self.LABEL_SCHEMA["mock_fallback_total"],
            registry=registry,
        )
        self.agent_limit = Counter(
            "agent_limit_total",
            "Agent executions stopped by a safety limit.",
            self.LABEL_SCHEMA["agent_limit_total"],
            registry=registry,
        )
        self.llm_tokens = Counter(
            "llm_tokens_total",
            "Tokens reported by the LLM provider.",
            self.LABEL_SCHEMA["llm_tokens_total"],
            registry=registry,
        )
        self.llm_cost_usd = Counter(
            "llm_cost_usd_total",
            "Cost in USD when explicitly reported by the provider.",
            self.LABEL_SCHEMA["llm_cost_usd_total"],
            registry=registry,
        )

        for result in ("valid", "repaired", "text_fallback", "invalid"):
            self.output_validation.labels(result=result)
        for provider in ("deepseek", "gigachat"):
            self.mock_fallback.labels(provider=provider)
        for limit in ("deadline", "loop_limit", "repeated_tool_call"):
            self.agent_limit.labels(limit=limit)

    def record_ai_request(self, provider: str, model: str, outcome: str) -> None:
        self.ai_requests.labels(provider=provider, model=model, outcome=outcome).inc()

    def observe_llm(
        self,
        provider: str,
        model: str,
        outcome: str,
        duration_seconds: float,
    ) -> None:
        self.llm_latency.labels(
            provider=provider,
            model=model,
            outcome=outcome,
        ).observe(max(duration_seconds, 0.0))

    def record_agent_step(self, node: str, outcome: str) -> None:
        self.agent_steps.labels(node=node, outcome=outcome).inc()

    def observe_tool(
        self,
        tool: str,
        outcome: str,
        duration_seconds: float,
    ) -> None:
        self.tool_calls.labels(tool=tool, outcome=outcome).inc()
        self.tool_latency.labels(tool=tool, outcome=outcome).observe(
            max(duration_seconds, 0.0)
        )

    def record_output_validation(self, result: str) -> None:
        self.output_validation.labels(result=result).inc()

    def record_mock_fallback(self, provider: str) -> None:
        self.mock_fallback.labels(provider=provider).inc()

    def record_agent_limit(self, limit: str) -> None:
        self.agent_limit.labels(limit=limit).inc()

    def record_usage(
        self,
        provider: str,
        model: str,
        usage: Mapping[str, Any] | None,
        response_metadata: Mapping[str, Any] | None = None,
    ) -> None:
        """Record only numeric usage fields explicitly returned by a provider."""

        if usage:
            for source, token_type in (
                ("input_tokens", "input"),
                ("output_tokens", "output"),
                ("total_tokens", "total"),
            ):
                value = usage.get(source)
                if isinstance(value, (int, float)) and not isinstance(value, bool):
                    if value >= 0:
                        self.llm_tokens.labels(
                            provider=provider,
                            model=model,
                            token_type=token_type,
                        ).inc(value)

        cost = self._reported_cost(response_metadata)
        if cost is not None and cost >= 0:
            self.llm_cost_usd.labels(provider=provider, model=model).inc(cost)

    @staticmethod
    def _reported_cost(metadata: Mapping[str, Any] | None) -> float | None:
        if not metadata:
            return None
        candidates: list[Any] = [
            metadata.get("total_cost"),
            metadata.get("cost"),
        ]
        token_usage = metadata.get("token_usage")
        if isinstance(token_usage, Mapping):
            candidates.extend(
                [token_usage.get("total_cost"), token_usage.get("cost")]
            )
        for value in candidates:
            if isinstance(value, (int, float)) and not isinstance(value, bool):
                return float(value)
        return None


AI_METRICS = AIMetrics()
