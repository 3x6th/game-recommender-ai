#!/usr/bin/env python3
"""
Test script for JSON parsing logic.
"""

import re
import json

def test_json_extraction():
    """Test the JSON extraction logic"""
    
    # Test case 1: Markdown code block with JSON
    content1 = '''```json
{
    "recommendations": [
        {
            "title": "Persona 5 Royal",
            "genre": "JRPG", 
            "description": "A stylish JRPG about a group of high school students who become Phantom Thieves",
            "why_recommended": "Perfect for JRPG fans who enjoy deep storytelling and unique art style",
            "platforms": ["PC", "PS4", "PS5", "Xbox One", "Xbox Series X", "Nintendo Switch"],
            "rating": 9.5,
            "release_year": "2019"
        }
    ]
}
```'''
    
    # Test case 2: Plain JSON without markdown
    content2 = '''{
    "recommendations": [
        {
            "title": "Final Fantasy VII Remake",
            "genre": "JRPG",
            "description": "Modern reimagining of the classic Final Fantasy VII",
            "why_recommended": "Excellent for fans of classic JRPGs with modern graphics",
            "platforms": ["PC", "PS4", "PS5"],
            "rating": 9.0,
            "release_year": "2020"
        }
    ]
}'''
    
    # Test case 3: Mixed content with JSON
    content3 = '''Here are some game recommendations:

```json
{
    "recommendations": [
        {
            "title": "Dragon Quest XI",
            "genre": "JRPG",
            "description": "Traditional JRPG with modern conveniences",
            "why_recommended": "Great for fans of classic turn-based JRPGs",
            "platforms": ["PC", "PS4", "PS5", "Xbox One", "Xbox Series X", "Nintendo Switch"],
            "rating": 8.8,
            "release_year": "2017"
        }
    ]
}
```

Hope you enjoy these games!'''
    
    def extract_json(content):
        """Extract JSON using the same logic as in DeepSeek service"""
        # Handle both markdown code blocks and plain JSON
        json_match = re.search(r'```(?:json)?\s*(\{.*?\})\s*```', content, re.DOTALL)
        if not json_match:
            # Try to find JSON without markdown
            json_match = re.search(r'(\{.*\})', content, re.DOTALL)
        
        if json_match:
            json_str = json_match.group(1)
            try:
                return json.loads(json_str)
            except json.JSONDecodeError:
                return None
        return None
    
    print("=== Testing JSON Extraction Logic ===")
    
    # Test 1
    print("\n1. Testing markdown code block:")
    result1 = extract_json(content1)
    if result1 and 'recommendations' in result1:
        print(f"✅ Success! Found {len(result1['recommendations'])} recommendations")
        print(f"   First game: {result1['recommendations'][0]['title']}")
    else:
        print("❌ Failed to extract JSON from markdown")
    
    # Test 2
    print("\n2. Testing plain JSON:")
    result2 = extract_json(content2)
    if result2 and 'recommendations' in result2:
        print(f"✅ Success! Found {len(result2['recommendations'])} recommendations")
        print(f"   First game: {result2['recommendations'][0]['title']}")
    else:
        print("❌ Failed to extract plain JSON")
    
    # Test 3
    print("\n3. Testing mixed content:")
    result3 = extract_json(content3)
    if result3 and 'recommendations' in result3:
        print(f"✅ Success! Found {len(result3['recommendations'])} recommendations")
        print(f"   First game: {result3['recommendations'][0]['title']}")
    else:
        print("❌ Failed to extract JSON from mixed content")
    
    print("\n=== Test Complete ===")

if __name__ == "__main__":
    test_json_extraction()
