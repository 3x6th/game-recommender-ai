import asyncio
import os
from types import SimpleNamespace
from unittest.mock import Mock, patch

import pytest

from app.services.deepseek_service import DeepSeekService


def sdk_response(content: str):
    return SimpleNamespace(
        choices=[SimpleNamespace(message=SimpleNamespace(content=content))]
    )


def test_plain_text_is_repaired_once_into_structured_output():
    async def run():
        service = DeepSeekService(api_key="test-key")
        service.client = Mock()
        service.client.chat.completions.create.side_effect = [
            sdk_response("Я помню: сначала советовал Elden Ring и Hades."),
            sdk_response(
                '{"reply":"Я помню первый ответ.","reasoning":"",'
                '"recommendations":[{"title":"Elden Ring"},{"title":"Hades"}]}'
            ),
        ]

        result = await service.get_recommendations_with_steam_library(
            user_message="Что ты советовал раньше?",
            selected_tags=[],
            steam_library=None,
        )

        assert service.client.chat.completions.create.call_count == 2
        assert result.reply == "Я помню первый ответ."
        assert [item["title"] for item in result.recommendations] == ["Elden Ring", "Hades"]

    asyncio.run(run())


def test_plain_text_survives_when_structured_repair_still_fails():
    async def run():
        service = DeepSeekService(api_key="test-key")
        service.client = Mock()
        original = "Я помню наш разговор и могу продолжить без новых карточек."
        service.client.chat.completions.create.side_effect = [
            sdk_response(original),
            sdk_response("всё ещё не json"),
        ]

        result = await service.get_recommendations_with_steam_library(
            user_message="Ты помнишь контекст?",
            selected_tags=[],
            steam_library=None,
        )

        assert result.reply == original
        assert result.recommendations == []

    asyncio.run(run())


def test_malformed_json_returns_error_in_production_instead_of_silent_mocks():
    async def run():
        service = DeepSeekService(api_key="test-key")
        service.client = Mock()
        service.client.chat.completions.create.side_effect = [
            sdk_response('{"recommendations": ['),
            sdk_response('{"recommendations": ['),
        ]

        with pytest.raises(ValueError, match="invalid after one repair"):
            await service.get_recommendations_with_steam_library(
                user_message="Посоветуй игру",
                selected_tags=[],
                steam_library=None,
            )

    asyncio.run(run())


def test_mock_fallback_is_opt_in_and_visibly_marked():
    async def run():
        with patch.dict(os.environ, {
            "AI_MOCK_FALLBACK_ENABLED": "true",
            "DEEPSEEK_API_KEY": "",
        }):
            service = DeepSeekService(api_key=None)
            result = await service.get_recommendations_with_steam_library(
                user_message="Посоветуй игру",
                selected_tags=[],
                steam_library=None,
            )

        assert result.recommendations
        assert "sample" in result.reply.lower()

    asyncio.run(run())
