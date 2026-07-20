import asyncio
import os
from unittest.mock import AsyncMock, patch

import pytest
from langchain_core.messages import AIMessage

from app.services.deepseek_service import DeepSeekService


def model_response(content: str) -> AIMessage:
    return AIMessage(content=content)


def test_plain_text_is_repaired_once_into_structured_output():
    async def run():
        chat_model = AsyncMock()
        chat_model.ainvoke.side_effect = [
            model_response("Я помню: сначала советовал Elden Ring и Hades."),
            model_response(
                '{"reply":"Я помню первый ответ.","reasoning":"",'
                '"recommendations":[{"title":"Elden Ring"},{"title":"Hades"}]}'
            ),
        ]
        service = DeepSeekService(api_key="test-key", chat_model=chat_model)

        result = await service.get_recommendations_with_steam_library(
            user_message="Что ты советовал раньше?",
            selected_tags=[],
            steam_library=None,
        )

        assert chat_model.ainvoke.await_count == 2
        assert result.reply == "Я помню первый ответ."
        assert [item["title"] for item in result.recommendations] == ["Elden Ring", "Hades"]

    asyncio.run(run())


def test_plain_text_survives_when_structured_repair_still_fails():
    async def run():
        chat_model = AsyncMock()
        original = "Я помню наш разговор и могу продолжить без новых карточек."
        chat_model.ainvoke.side_effect = [
            model_response(original),
            model_response("всё ещё не json"),
        ]
        service = DeepSeekService(api_key="test-key", chat_model=chat_model)

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
        chat_model = AsyncMock()
        chat_model.ainvoke.side_effect = [
            model_response('{"recommendations": ['),
            model_response('{"recommendations": ['),
        ]
        service = DeepSeekService(api_key="test-key", chat_model=chat_model)

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
