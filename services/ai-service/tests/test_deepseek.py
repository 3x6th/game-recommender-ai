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

async def test_deepseek_service():
    """Test the improved DeepSeek service with mocked API calls"""
    
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
        
        # Initialize service
        service = DeepSeekService()
        
        print("=== DeepSeek Service Test (Mocked) ===")
        print(f"API Key configured: {bool(service.api_key)}")
        print(f"Service available: {await service.is_available()}")
        print(f"Circuit breaker status: {service.get_circuit_breaker_status()}")
        print()
        
        # Test recommendations
        print("🔄 Testing game recommendations...")
        try:
            start_time = asyncio.get_event_loop().time()
            recommendations = await service.get_recommendations(
                preferences="I like RPG games with good story",
                genres=["RPG", "Action"],
                platforms=["PC", "PS5"],
                max_recommendations=3
            )
            end_time = asyncio.get_event_loop().time()
            
            print(f"✅ Recommendations received in {end_time - start_time:.2f} seconds")
            print(f"   Count: {len(recommendations)}")
            for i, rec in enumerate(recommendations, 1):
                print(f"   {i}. {rec.get('title', 'Unknown')} ({rec.get('genre', 'Unknown')})")
            
        except Exception as e:
            print(f"❌ Error getting recommendations: {e}")
        
        print()
    
    # Test chat
    print("💬 Testing chat functionality...")
    try:
        start_time = asyncio.get_event_loop().time()
        response = await service.chat(
            message="What are the best JRPG games?",
            context="User is asking about Japanese RPG recommendations"
        )
        end_time = asyncio.get_event_loop().time()
        
        print(f"✅ Chat response received in {end_time - start_time:.2f} seconds")
        print(f"   Response: {response[:100]}...")
        
    except Exception as e:
        print(f"❌ Error in chat: {e}")
    
    print()
    
    # Final status
    print("📊 Final service status:")
    print(f"   Circuit breaker: {service.get_circuit_breaker_status()}")
    print(f"   Available: {await service.is_available()}")

if __name__ == "__main__":
    asyncio.run(test_deepseek_service())
