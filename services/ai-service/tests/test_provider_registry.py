import asyncio
import os
from unittest.mock import patch

import pytest

from app.services.gigachat_service import GigaChatService
from app.services.registry import ServiceRegistry


def test_gigachat_api_key_does_not_register_hardcoded_mock() -> None:
    with patch.dict(os.environ, {"GIGACHAT_API_KEY": "secret"}, clear=True):
        registry = ServiceRegistry()

    assert registry.get_available_services() == []
    assert registry.get_active_provider() == "none"


def test_explicit_gigachat_mock_is_named_and_visibly_marked() -> None:
    async def run() -> None:
        with patch.dict(os.environ, {"GIGACHAT_MOCK_ENABLED": "true"}, clear=True):
            registry = ServiceRegistry()
            result = await registry.get_recommendations_with_steam_library(
                user_message="recommend a game",
                selected_tags=[],
                steam_library=None,
            )

        assert registry.get_active_provider() == "GigaChatMockService"
        assert result.recommendations
        assert "sample" in result.reply.lower()
        assert all(
            "sample" in item["why_recommended"].lower()
            for item in result.recommendations
        )

    asyncio.run(run())


def test_real_deepseek_remains_preferred_over_explicit_development_mock() -> None:
    with patch.dict(
        os.environ,
        {
            "DEEPSEEK_API_KEY": "test-key",
            "GIGACHAT_MOCK_ENABLED": "true",
        },
        clear=True,
    ):
        registry = ServiceRegistry()

    assert registry.get_active_provider() == "DeepSeekService"
    assert registry.get_available_services() == [
        "DeepSeekService",
        "GigaChatMockService",
    ]


def test_registry_without_real_or_explicit_mock_provider_fails_cleanly() -> None:
    async def run() -> None:
        with patch.dict(os.environ, {}, clear=True):
            registry = ServiceRegistry()

        with pytest.raises(RuntimeError, match="No active AI service"):
            await registry.get_recommendations("recommend a game")

    asyncio.run(run())


def test_gigachat_service_cannot_return_samples_without_explicit_flag() -> None:
    async def run() -> None:
        service = GigaChatService(api_key="secret", mock_enabled=False)

        assert await service.is_available() is False
        with pytest.raises(RuntimeError, match="not implemented"):
            await service.get_recommendations("recommend a game")

    asyncio.run(run())
