#!/usr/bin/env python3
"""
Test script for the AI Service.
Tests both gRPC server and DeepSeek integration.
"""

import asyncio
import os
import sys
from pathlib import Path
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()

# Add the app directory to Python path
sys.path.insert(0, str(Path(__file__).parent / "app"))

from app.services.deepseek_service import DeepSeekService
from app.services.registry import ServiceRegistry

async def test_deepseek_service():
    """Test DeepSeek service directly"""
    print("🧪 Testing DeepSeek Service...")
    
    # Create service instance
    service = DeepSeekService()
    
    # Test recommendations
    print("\n📋 Testing recommendations...")
    recommendations = await service.get_recommendations(
        preferences="I like action RPGs with good story",
        genres=["RPG", "Action"],
        platforms=["PC", "PS5"],
        max_recommendations=3
    )
    
    print(f"✅ Got {len(recommendations)} recommendations:")
    for i, rec in enumerate(recommendations, 1):
        print(f"  {i}. {rec['title']} ({rec['genre']}) - {rec['rating']}/10")
    
    # Test chat
    print("\n💬 Testing chat...")
    chat_response = await service.chat(
        message="What are the best RPGs of 2023?",
        context="User is asking about recent RPG games"
    )
    
    print(f"✅ Chat response: {chat_response[:100]}...")
    
    # Test availability
    print(f"\n🔑 Service available: {await service.is_available()}")

async def test_service_registry():
    """Test service registry"""
    print("\n🧪 Testing Service Registry...")
    
    registry = ServiceRegistry()
    
    # Test getting active provider
    active_provider = registry.get_active_provider()
    print(f"✅ Active provider: {active_provider}")
    
    # Test recommendations through registry
    print("\n📋 Testing recommendations through registry...")
    recommendations = await registry.get_recommendations(
        preferences="I want strategy games",
        max_recommendations=2
    )
    
    print(f"✅ Got {len(recommendations)} recommendations through registry")
    
    # Test chat through registry
    print("\n💬 Testing chat through registry...")
    chat_response = await registry.chat("Tell me about indie games")
    print(f"✅ Chat response: {chat_response[:100]}...")

async def main():
    """Main test function"""
    print("🚀 Starting AI Service Tests...")
    
    try:
        await test_deepseek_service()
        await test_service_registry()
        
        print("\n🎉 All tests completed successfully!")
        
    except Exception as e:
        print(f"\n❌ Test failed: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    asyncio.run(main())
