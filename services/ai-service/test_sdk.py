#!/usr/bin/env python3
"""
Test script for DeepSeek SDK integration.
"""

import logging
import os
import time
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)

def test_deepseek_sdk():
    """Test DeepSeek SDK directly"""
    
    try:
        from deepseek_ai import DeepSeekAI
        
        api_key = os.getenv('DEEPSEEK_API_KEY')
        if not api_key:
            print("❌ No DEEPSEEK_API_KEY found in environment")
            return
        
        print("🚀 Testing DeepSeek SDK...")
        
        # Initialize client
        client = DeepSeekAI(api_key=api_key)
        print("✅ SDK client initialized")
        
        # Test simple chat
        print("\n💬 Testing simple chat...")
        start_time = time.time()
        
        response = client.chat.completions.create(
            model="deepseek-chat",
            messages=[{"role": "user", "content": "Hello! What is JRPG?"}],
            max_tokens=200,
            temperature=0.7
        )
        
        end_time = time.time()
        elapsed_time = end_time - start_time
        
        print(f"✅ Response received in {elapsed_time:.2f} seconds")
        print(f"📝 Content: {response.choices[0].message.content[:100]}...")
        
        # Test response structure
        print(f"🔍 Response type: {type(response)}")
        print(f"🔍 Response attributes: {[attr for attr in dir(response) if not attr.startswith('_')]}")
        
        # Try to access response data
        if hasattr(response, 'choices') and response.choices:
            print(f"📊 Choices count: {len(response.choices)}")
            if hasattr(response.choices[0], 'message'):
                print(f"📝 First choice message: {response.choices[0].message.content[:100]}...")
        else:
            print("⚠️ No choices found in response")
        
        print("\n✅ SDK test completed successfully!")
        
    except ImportError:
        print("❌ DeepSeek SDK not installed. Run: poetry install")
    except Exception as e:
        print(f"❌ SDK test failed: {e}")

if __name__ == "__main__":
    test_deepseek_sdk()
