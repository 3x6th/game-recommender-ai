"""Optional live smoke test for the async LangChain DeepSeek integration."""

import asyncio
import os

import pytest
from langchain_core.messages import AIMessage, HumanMessage
from langchain_deepseek import ChatDeepSeek
from pydantic import SecretStr


@pytest.mark.integration
def test_deepseek_live_async_integration() -> None:
    api_key = os.getenv("DEEPSEEK_API_KEY")
    if not api_key:
        pytest.skip("DEEPSEEK_API_KEY is not configured")

    async def run() -> None:
        model = ChatDeepSeek(
            model="deepseek-chat",
            api_key=SecretStr(api_key),
            max_tokens=50,
            temperature=0,
            timeout=20,
            max_retries=1,
        )
        response = await model.ainvoke(
            [HumanMessage(content="Reply with one word: pong")]
        )

        assert isinstance(response, AIMessage)
        assert response.content

    asyncio.run(run())
