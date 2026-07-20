import asyncio
import logging
from collections.abc import Sequence
from typing import Any
from unittest.mock import AsyncMock, patch

import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage, BaseMessage
from langchain_core.tools import BaseTool, tool
from prometheus_client import CollectorRegistry, generate_latest

from app.agent.workflow import (
    AgentDeadlineError,
    AgentLoopLimitError,
    AgentRequest,
    AgentWorkflow,
    AgentWorkflowConfig,
)
from app.http_api import create_app
from app.observability import AIMetrics
from app.services.base import RecommendationResult
from app.services.deepseek_service import DeepSeekService


class ScriptedModel:
    def __init__(self, responses: Sequence[AIMessage]) -> None:
        self.responses = list(responses)

    def bind_tools(self, tools: Sequence[BaseTool]) -> "ScriptedModel":
        return self

    async def ainvoke(self, messages: list[BaseMessage]) -> AIMessage:
        return self.responses.pop(0)


async def reply_finalizer(content: str) -> RecommendationResult:
    return RecommendationResult(reply=content)


def request() -> AgentRequest:
    return AgentRequest(
        system_prompt="system",
        user_message="secret-user-message",
        request_id="request with\nnewline",
    )


def tool_call(name: str, call_id: str, **arguments: Any) -> AIMessage:
    return AIMessage(
        content="",
        tool_calls=[
            {
                "name": name,
                "args": arguments,
                "id": call_id,
                "type": "tool_call",
            }
        ],
    )


def test_metric_schema_excludes_high_cardinality_identifiers() -> None:
    forbidden = {"request_id", "requestId", "run_id", "chat_id", "user_id"}

    assert all(
        forbidden.isdisjoint(label_names)
        for label_names in AIMetrics.LABEL_SCHEMA.values()
    )


def test_metrics_endpoint_exposes_prometheus_registry() -> None:
    registry = CollectorRegistry()
    metrics = AIMetrics(registry)
    metrics.record_ai_request("deepseek", "deepseek-chat", "success")
    metrics.record_usage(
        "deepseek",
        "deepseek-chat",
        {"total_tokens": 7},
        {"total_cost": 0.001},
    )

    response = TestClient(create_app(registry)).get("/metrics")

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/plain")
    assert (
        'ai_requests_total{model="deepseek-chat",outcome="success",provider="deepseek"} 1.0'
        in response.text
    )
    assert registry.get_sample_value(
        "llm_cost_usd_total",
        {"provider": "deepseek", "model": "deepseek-chat"},
    ) == pytest.approx(0.001)


def test_agent_records_model_tool_steps_usage_and_safe_log(caplog: pytest.LogCaptureFixture) -> None:
    @tool
    async def search_games(query: str) -> dict[str, Any]:
        """Search games in the internal catalog."""
        return {"games": [{"app_id": 620, "name": "Portal 2"}]}

    async def run() -> None:
        registry = CollectorRegistry()
        metrics = AIMetrics(registry)
        model = ScriptedModel(
            [
                tool_call("search_games", "call-1", query="secret-tool-input"),
                AIMessage(
                    content="final answer",
                    usage_metadata={
                        "input_tokens": 12,
                        "output_tokens": 3,
                        "total_tokens": 15,
                    },
                ),
            ]
        )
        workflow = AgentWorkflow(
            model,
            [search_games],
            reply_finalizer,
            metrics=metrics,
            provider="deepseek",
            model_name="deepseek-chat",
        )

        with caplog.at_level(logging.INFO, logger="app.agent.workflow"):
            result = await workflow.run(request())

        assert result.reply == "final answer"
        assert registry.get_sample_value(
            "tool_calls_total",
            {"tool": "search_games", "outcome": "success"},
        ) == 1
        assert registry.get_sample_value(
            "agent_steps_total",
            {"node": "model", "outcome": "success"},
        ) == 2
        assert registry.get_sample_value(
            "agent_steps_total",
            {"node": "tools", "outcome": "success"},
        ) == 1
        assert registry.get_sample_value(
            "llm_tokens_total",
            {
                "provider": "deepseek",
                "model": "deepseek-chat",
                "token_type": "total",
            },
        ) == 15
        log_output = caplog.text
        assert "request_id=request_with_newline" in log_output
        assert "run_id=" in log_output
        assert "provider=deepseek model=deepseek-chat" in log_output
        assert "node=tools iteration=1 tool=search_games status=success" in log_output
        assert "secret-user-message" not in log_output
        assert "secret-tool-input" not in log_output

    asyncio.run(run())


def test_agent_records_loop_limit_without_identifier_labels() -> None:
    @tool
    async def search_games(query: str) -> str:
        """Search games in the internal catalog."""
        return query

    async def run() -> None:
        registry = CollectorRegistry()
        metrics = AIMetrics(registry)
        workflow = AgentWorkflow(
            ScriptedModel(
                [
                    tool_call("search_games", "call-1", query="one"),
                    tool_call("search_games", "call-2", query="two"),
                ]
            ),
            [search_games],
            reply_finalizer,
            config=AgentWorkflowConfig(max_tool_iterations=1),
            metrics=metrics,
        )

        with pytest.raises(AgentLoopLimitError):
            await workflow.run(request())

        assert registry.get_sample_value(
            "agent_limit_total",
            {"limit": "loop_limit"},
        ) == 1

        class SlowModel:
            async def ainvoke(self, messages: list[BaseMessage]) -> AIMessage:
                await asyncio.sleep(0.02)
                return AIMessage(content="late")

        deadline_workflow = AgentWorkflow(
            SlowModel(),
            [],
            reply_finalizer,
            config=AgentWorkflowConfig(deadline_seconds=0.001),
            metrics=metrics,
        )
        with pytest.raises(AgentDeadlineError):
            await deadline_workflow.run(request())
        assert registry.get_sample_value(
            "agent_limit_total",
            {"limit": "deadline"},
        ) == 1

    asyncio.run(run())


def test_failed_tool_has_bounded_error_metric() -> None:
    @tool
    async def broken_tool(query: str) -> str:
        """Fail without leaking tool payloads or error details."""
        raise RuntimeError(query)

    async def run() -> None:
        registry = CollectorRegistry()
        metrics = AIMetrics(registry)
        workflow = AgentWorkflow(
            ScriptedModel(
                [
                    tool_call("broken_tool", "call-1", query="private-query"),
                    AIMessage(content="fallback answer"),
                ]
            ),
            [broken_tool],
            reply_finalizer,
            metrics=metrics,
        )

        await workflow.run(request())

        assert registry.get_sample_value(
            "tool_calls_total",
            {"tool": "broken_tool", "outcome": "error"},
        ) == 1

        unknown_workflow = AgentWorkflow(
            ScriptedModel(
                [
                    tool_call("model_invented_tool_123", "call-2"),
                    AIMessage(content="unknown tool handled"),
                ]
            ),
            [],
            reply_finalizer,
            metrics=metrics,
        )
        await unknown_workflow.run(request())
        assert registry.get_sample_value(
            "tool_calls_total",
            {"tool": "unknown", "outcome": "unknown_tool"},
        ) == 1

    asyncio.run(run())


def test_output_guard_and_mock_fallback_outcomes_are_exported() -> None:
    async def run() -> None:
        registry = CollectorRegistry()
        metrics = AIMetrics(registry)
        model = AsyncMock()
        model.ainvoke.return_value = AIMessage(
            content=(
                '{"reply":"ok","reasoning":"",'
                '"recommendations":[{"title":"Portal 2"}]}'
            )
        )
        service = DeepSeekService(
            api_key="test-key",
            chat_model=model,
            metrics=metrics,
        )

        await service.get_recommendations_with_steam_library(
            user_message="Find a game",
            selected_tags=[],
            steam_library=None,
        )

        with patch.dict(
            "os.environ",
            {"AI_MOCK_FALLBACK_ENABLED": "true", "DEEPSEEK_API_KEY": ""},
            clear=False,
        ):
            fallback_service = DeepSeekService(api_key=None, metrics=metrics)
            await fallback_service.get_recommendations_with_steam_library(
                user_message="Find a game",
                selected_tags=[],
                steam_library=None,
            )

        assert registry.get_sample_value(
            "output_validation_total",
            {"result": "valid"},
        ) == 1
        assert registry.get_sample_value(
            "mock_fallback_total",
            {"provider": "deepseek"},
        ) == 1
        exposition = generate_latest(registry).decode()
        assert "request_id" not in exposition
        assert "run_id" not in exposition

    asyncio.run(run())


def test_output_guard_exports_repaired_text_fallback_and_invalid() -> None:
    async def run() -> None:
        registry = CollectorRegistry()
        metrics = AIMetrics(registry)

        repaired_model = AsyncMock()
        repaired_model.ainvoke.return_value = AIMessage(
            content=(
                '{"reply":"repaired","reasoning":"",'
                '"recommendations":[]}'
            )
        )
        repaired_service = DeepSeekService(
            api_key="test-key",
            chat_model=repaired_model,
            metrics=metrics,
        )
        repaired = await repaired_service._guard_content("plain answer", 5)

        fallback_model = AsyncMock()
        fallback_model.ainvoke.return_value = AIMessage(content="still plain")
        fallback_service = DeepSeekService(
            api_key="test-key",
            chat_model=fallback_model,
            metrics=metrics,
        )
        fallback = await fallback_service._guard_content("original plain answer", 5)

        invalid_model = AsyncMock()
        invalid_model.ainvoke.return_value = AIMessage(
            content='{"recommendations": ['
        )
        invalid_service = DeepSeekService(
            api_key="test-key",
            chat_model=invalid_model,
            metrics=metrics,
        )
        with pytest.raises(ValueError, match="invalid after one repair"):
            await invalid_service._guard_content('{"recommendations": [', 5)

        assert repaired.reply == "repaired"
        assert fallback.reply == "original plain answer"
        for result in ("repaired", "text_fallback", "invalid"):
            assert registry.get_sample_value(
                "output_validation_total",
                {"result": result},
            ) == 1

    asyncio.run(run())
