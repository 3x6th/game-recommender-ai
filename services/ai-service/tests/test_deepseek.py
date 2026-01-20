#!/usr/bin/env python3
"""
Test script for DeepSeek service improvements.
"""

import asyncio
import logging
import os
from unittest.mock import AsyncMock, patch
from dotenv import load_dotenv

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
    
        # Mock the DeepSeek SDK to avoid real API calls
        with patch('app.services.deepseek_service.DeepSeekAI') as mock_sdk:
            # Mock SDK methods (create is synchronous, not async)
            mock_client = AsyncMock()
            mock_response = {
                "choices": [{
                    "message": {
                        "content": '{"recommendations": [{"title": "The Witcher 3", "genre": "RPG"}, {"title": "Elden Ring", "genre": "Action RPG"}]}'
                    }
                }]
            }
            # Create is synchronous, not async
            from unittest.mock import Mock
            mock_client.chat.completions.create = Mock(return_value=mock_response)
            mock_sdk.return_value = mock_client

            service = DeepSeekService()
            recommendations = await service.get_recommendations(
                preferences="I like RPG games with good story",
                genres=["RPG", "Action"],
                platforms=["PC", "PS5"],
                max_recommendations=3,
            )

            assert isinstance(recommendations, list)
            assert len(recommendations) <= 3

            available = await service.is_available()
            assert isinstance(available, bool)

    asyncio.run(run())

if __name__ == "__main__":
    test_deepseek_service()
