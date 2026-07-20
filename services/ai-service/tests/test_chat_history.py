import asyncio
from unittest.mock import AsyncMock

from langchain_core.messages import AIMessage

from app.services.deepseek_service import DeepSeekService


def test_history_is_sent_as_chat_messages_before_current_user_message():
    async def run():
        chat_model = AsyncMock()
        chat_model.ainvoke.return_value = AIMessage(
            content='{"reasoning":"ok","recommendations":[]}'
        )
        service = DeepSeekService(api_key="test-key", chat_model=chat_model)

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

        messages = chat_model.ainvoke.call_args.args[0]
        assert [message.type for message in messages] == ["system", "human", "ai", "human"]
        assert messages[-1].content == "Only co-op among those"
        assert sum(message.content == "Only co-op among those" for message in messages) == 1
        assert result.recommendations == []
        assert result.reasoning == "ok"

    asyncio.run(run())
