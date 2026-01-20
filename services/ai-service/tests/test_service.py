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

def test_deepseek_service():
    """Basic sanity test for DeepSeekService without pytest-asyncio."""
    async def run():
        service = DeepSeekService()
        recommendations = await service.get_recommendations(
            preferences="I like action RPGs with good story",
            genres=["RPG", "Action"],
            platforms=["PC", "PS5"],
            max_recommendations=3,
        )
        assert isinstance(recommendations, list)
        assert len(recommendations) <= 3

        available = await service.is_available()
        assert isinstance(available, bool)

    asyncio.run(run())

def test_service_registry():
    """Basic sanity test for ServiceRegistry without pytest-asyncio."""
    async def run():
        registry = ServiceRegistry()

        active_provider = registry.get_active_provider()
        assert isinstance(active_provider, str)

        recommendations = await registry.get_recommendations(
            preferences="I want strategy games",
            max_recommendations=2,
        )
        assert isinstance(recommendations, list)

    asyncio.run(run())
    

if __name__ == "__main__":
    test_deepseek_service()
    test_service_registry()
