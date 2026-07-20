#!/usr/bin/env python3
"""
Test script for DeepSeek service improvements.
"""

import asyncio
import logging
from unittest.mock import AsyncMock
from dotenv import load_dotenv
from langchain_core.messages import AIMessage

# Load environment variables
load_dotenv()

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)

from app.services.deepseek_service import DeepSeekService

def test_deepseek_service():
    """Test the improved DeepSeek service with mocked API calls (no pytest-asyncio required)."""
    async def run():
        chat_model = AsyncMock()
        chat_model.ainvoke.return_value = AIMessage(
            content=(
                '{"recommendations": [{"title": "The Witcher 3", "genre": "RPG"}, '
                '{"title": "Elden Ring", "genre": "Action RPG"}]}'
            )
        )
        service = DeepSeekService(api_key="test-key", chat_model=chat_model)
        recommendations = await service.get_recommendations(
            preferences="I like RPG games with good story",
            genres=["RPG", "Action"],
            platforms=["PC", "PS5"],
            max_recommendations=3,
        )

        assert isinstance(recommendations, list)
        assert len(recommendations) <= 3
        assert chat_model.ainvoke.await_count == 1

        available = await service.is_available()
        assert isinstance(available, bool)

    asyncio.run(run())

if __name__ == "__main__":
    test_deepseek_service()
