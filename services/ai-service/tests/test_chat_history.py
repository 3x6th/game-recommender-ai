import asyncio
from types import SimpleNamespace
from unittest.mock import Mock

from app.services.deepseek_service import DeepSeekService


def test_history_is_sent_as_chat_messages_before_current_user_message():
    async def run():
        service = DeepSeekService(api_key="test-key")
        service.client = Mock()
        service.client.chat.completions.create.return_value = SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(content='{"reasoning":"ok","recommendations":[]}'))]
        )

        result = await service.get_recommendations_with_steam_library(
            user_message="Only co-op among those",
            selected_tags=["Co-op"],
            steam_library=None,
            max_recommendations=3,
            history=[
                {"role": "user", "content": "Recommend space games"},
                {"role": "assistant", "content": "Recommended games: Outer Wilds; No Man's Sky"},
            ],
        )

        messages = service.client.chat.completions.create.call_args.kwargs["messages"]
        assert [message["role"] for message in messages] == ["system", "user", "assistant", "user"]
        assert messages[-1]["content"] == "Only co-op among those"
        assert sum(message["content"] == "Only co-op among those" for message in messages) == 1
        assert result.recommendations == []
        assert result.reasoning == "ok"

    asyncio.run(run())
