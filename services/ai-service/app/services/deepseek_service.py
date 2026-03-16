"""
DeepSeek AI service implementation using official SDK.
"""

import logging
import os
import json
import asyncio
import time
import re
from typing import List, Dict, Any

from deepseek_ai import DeepSeekAI
from json_repair import repair_json
from app.services.base import BaseAIService

logger = logging.getLogger(__name__)

class DeepSeekService(BaseAIService):
    """DeepSeek AI service provider using official SDK"""
    
    def __init__(self, api_key: str = None):
        super().__init__(api_key or os.getenv('DEEPSEEK_API_KEY'))
        self.model = "deepseek-chat"
        
        # Initialize DeepSeek client
        if self.api_key:
            self.client = DeepSeekAI(api_key=self.api_key)
        else:
            self.client = None
        
        # Circuit breaker state
        self.failure_count = 0
        self.last_failure_time = 0
        self.circuit_open = False
        self.circuit_open_timeout = 60  # 1 minute
        
    def _is_circuit_open(self) -> bool:
        """Check if circuit breaker is open"""
        if not self.circuit_open:
            return False
        
        # Check if enough time has passed to try again
        if time.time() - self.last_failure_time > self.circuit_open_timeout:
            self.circuit_open = False
            self.failure_count = 0
            logger.info("Circuit breaker closed, allowing requests again")
            return False
        
        return True
    
    def _record_failure(self):
        """Record a failure and potentially open circuit breaker"""
        self.failure_count += 1
        self.last_failure_time = time.time()
        
        # Open circuit breaker after 3 consecutive failures
        if self.failure_count >= 3:
            self.circuit_open = True
            logger.warning("Circuit breaker opened due to multiple failures")
    
    def _record_success(self):
        """Record a successful request"""
        self.failure_count = 0
        if self.circuit_open:
            self.circuit_open = False
            logger.info("Circuit breaker closed after successful request")

    def _extract_json_string(self, content: str) -> str | None:
        """Extract JSON object from LLM content (code block or raw)."""
        # Prefer fenced JSON blocks if present
        json_match = re.search(
            r'```(?:json)?\s*(\{[\s\S]*\})\s*```',
            content,
            re.IGNORECASE,
        )
        if json_match:
            return json_match.group(1)

        # Fallback: any JSON-looking object in the text
        json_match = re.search(r'(\{[\s\S]*\})', content)
        if json_match:
            return json_match.group(1)

        return None

    def _loads_json_with_repair(self, json_str: str) -> Dict[str, Any] | None:
        """Parse JSON, attempting repair on malformed LLM output."""
        try:
            parsed = json.loads(json_str)
            return parsed if isinstance(parsed, dict) else None
        except json.JSONDecodeError as e:
            logger.warning(f"Standard JSON parsing failed: {e}. Trying JSON repair.")

        try:
            repaired = repair_json(json_str)
            parsed = json.loads(repaired)
            if isinstance(parsed, dict):
                logger.info("JSON repaired successfully")
                return parsed
        except Exception as repair_error:
            logger.warning(f"Failed to repair JSON from chat response: {repair_error}")

        return None

    def _parse_recommendations_from_content(
        self,
        content: str,
        max_recommendations: int,
    ) -> Dict[str, Any] | None:
        json_str = self._extract_json_string(content)
        if not json_str:
            return None

        logger.info(f"Extracted JSON string: {json_str[:200]}...")
        parsed_response = self._loads_json_with_repair(json_str)
        if not parsed_response:
            return None

        if 'recommendations' in parsed_response:
            # Обрезаем рекомендации до максимума
            if isinstance(parsed_response['recommendations'], list):
                parsed_response['recommendations'] = parsed_response['recommendations'][:max_recommendations]
            return parsed_response

        logger.warning(
            "JSON parsed but no 'recommendations' list found. Keys: %s",
            list(parsed_response.keys()),
        )
        return None
    
    async def get_recommendations(
        self,
        preferences: str,
        genres: List[str] = None,
        platforms: List[str] = None,
        max_recommendations: int = 5
    ) -> Dict[str, Any]:
        """Get game recommendations from DeepSeek. Returns dict with reasoning and recommendations."""
        try:
            if not self.api_key or not self.client:
                logger.warning("No DeepSeek API key or client available")
                return self._get_mock_recommendations(max_recommendations)
            
            # Check circuit breaker
            if self._is_circuit_open():
                logger.warning("Circuit breaker is open, returning mock data")
                return self._get_mock_recommendations(max_recommendations)
            
            logger.info(f"Getting recommendations from DeepSeek: {preferences}")
            
            # Prepare prompt for DeepSeek
            prompt = f"""
            You are a game recommendation AI. Based on the following user preferences, recommend {max_recommendations} video games.

            User Preferences: {preferences}
            Preferred Genres: {', '.join(genres) if genres else 'Any'}
            Preferred Platforms: {', '.join(platforms) if platforms else 'Any'}

            IMPORTANT: You must respond with ONLY valid JSON in this exact format, no additional text:
            {{
                "reasoning": "A brief explanation (2-4 sentences) of why these particular games were chosen, based on what criteria (preferences, genres, platforms)",
                "recommendations": [
                    {{
                        "title": "Game Title",
                        "genre": "Game Genre", 
                        "description": "Brief description",
                        "why_recommended": "Why this game matches preferences",
                        "platforms": ["PC", "PS5", "Xbox"],
                        "rating": 8.5,
                        "release_year": "2023"
                    }}
                ]
            }}

            Reply in the language of the user's message!
            Focus on games that best match the user's preferences. Do not include any text before or after the JSON.
            """
            
            # Call DeepSeek API with retry logic
            response = await self._call_deepseek_api_with_retry(prompt)

            if response and 'recommendations' in response and 'reasoning' in response:
                        self._record_success()
                        if isinstance(response['recommendations'], list):
                            response['recommendations'] = response['recommendations'][:max_recommendations]
                        return response
            elif response and 'choices' in response and len(response['choices']) > 0:
                # Try to parse content from chat response
                content = response['choices'][0]['message']['content']
                logger.info(f"Parsing recommendations from chat response: {content[:200]}...")

                json_recommendations = self._parse_recommendations_from_content(content, max_recommendations)
                if json_recommendations is not None:
                    self._record_success()
                    logger.info("Successfully parsed JSON recommendations from chat response")
                    return json_recommendations

                logger.warning("Failed to parse JSON recommendations from chat response; falling back to text extraction")
                logger.warning(f"Raw content: {content[:500]}...")
                
                # Try to extract recommendations manually from text
                extracted_recommendations = self._extract_recommendations_from_text(content, max_recommendations)
                if extracted_recommendations:
                    self._record_success()
                    logger.info("Successfully extracted recommendations from text response")
                    return {
                                "reasoning": "",
                                "recommendations": extracted_recommendations[:max_recommendations]
                            }
                
                # For now, return mock data but log the actual response for analysis
                logger.info(f"Full DeepSeek response content: {content}")
                
            # Log the full response for debugging
            logger.warning(f"Invalid response structure from DeepSeek. Response keys: {list(response.keys()) if isinstance(response, dict) else 'Not a dict'}")
            logger.warning("Using mock data")
            return self._get_mock_recommendations(max_recommendations)
            
        except Exception as e:
            self._record_failure()
            logger.error(f"Error getting recommendations from DeepSeek: {e}")
            return self._get_mock_recommendations(max_recommendations)

    async def get_recommendations_with_steam_library(
            self,
            user_message: str,
            selected_tags: List[str],
            steam_library: Dict[str, Any],
            max_recommendations: int = 5
    ) -> Dict[str, Any]:
        """Get recommendations based on user preferences and Steam library"""
        try:
            if not self.api_key or not self.client:
                logger.warning("No DeepSeek API key or client available")
                return self._get_mock_recommendations(max_recommendations)

            if self._is_circuit_open():
                logger.warning("Circuit breaker is open, returning mock data")
                return self._get_mock_recommendations(max_recommendations)

            # Process Steam library data
            played_games = []
            if steam_library and hasattr(steam_library, 'response') and steam_library.response.games:
                for game in steam_library.response.games:
                    played_games.append({
                        'name': game.name,
                        'playtime': game.playtimeForever,
                        'recent_playtime': game.playtime2weeks
                    })

            # Sort games by playtime to identify favorites
            played_games.sort(key=lambda x: x['playtime'], reverse=True)
            favorite_games = played_games[:5] if played_games else []

            # Prepare prompt with Steam library context
            prompt = f"""
            You are a game recommendation AI. Based on the following user information and Steam library data, recommend {max_recommendations} video games.

            User Message: {user_message}
            Selected Tags/Genres: {', '.join(selected_tags) if selected_tags else 'Any'}

            Steam Library Analysis:
            - Top played games: {', '.join(f"{game['name']} ({game['playtime']} hours)" for game in favorite_games)}
            - Total games owned: {len(played_games)}

            IMPORTANT: Recommend games that:
            1. Match user's preferences from their message
            2. Are similar to their most played games
            3. Align with their selected tags
            4. Reply in the language of the user's message!

            RESPOND WITH ONLY valid JSON in this exact format:
            {{
                "reasoning": "A brief explanation (2-4 sentences) of why these particular games were chosen, with a link to the Steam library: which games/genres/patterns influenced the choice",
                "recommendations": [
                    {{
                        "title": "Game Title",
                        "genre": "Game Genre",
                        "description": "Brief description",
                        "why_recommended": "Explain why this game matches their preferences and play history",
                        "platforms": ["PC", "PS5", "Xbox"],
                        "rating": 8.5,
                        "release_year": "2023"
                    }}
                ]
            }}
            """

            # Call DeepSeek API with retry logic
            response = await self._call_deepseek_api_with_retry(prompt)

            # Process response (using existing response handling logic)
            if response and 'recommendations' in response and 'reasoning' in response:
                self._record_success()
                if isinstance(response['recommendations'], list):
                    response['recommendations'] = response['recommendations'][:max_recommendations]
                return response
            elif response and 'choices' in response and len(response['choices']) > 0:
                # Try to parse content from chat response
                content = response['choices'][0]['message']['content']
                logger.info(f"Parsing recommendations from chat response: {content[:200]}...")

                json_recommendations = self._parse_recommendations_from_content(content, max_recommendations)
                if json_recommendations is not None:
                    self._record_success()
                    logger.info("Successfully parsed JSON recommendations from chat response")
                    return json_recommendations

                logger.warning("Failed to parse JSON recommendations from chat response; falling back to text extraction")
                logger.warning(f"Raw content: {content[:500]}...")

                # Try to extract recommendations manually from text
                extracted_recommendations = self._extract_recommendations_from_text(content, max_recommendations)
                if extracted_recommendations:
                    self._record_success()
                    logger.info("Successfully extracted recommendations from text response")
                    return {
                            "reasoning": "",
                            "recommendations": extracted_recommendations[:max_recommendations]
                        }

                # For now, return mock data but log the actual response for analysis
                logger.info(f"Full DeepSeek response content: {content}")

                # Log the full response for debugging
            logger.warning(
                f"Invalid response structure from DeepSeek. Response keys: {list(response.keys()) if isinstance(response, dict) else 'Not a dict'}")
            logger.warning("Using mock data")

            return self._get_mock_recommendations(max_recommendations)

        except Exception as e:
            self._record_failure()
            logger.error(f"Error getting recommendations with Steam library: {e}")
            return self._get_mock_recommendations(max_recommendations)

    async def _call_deepseek_api_with_retry(self, prompt: str, is_chat: bool = False) -> Dict[str, Any]:
        """Make API call to DeepSeek with retry logic using SDK"""
        start_time = time.time()
        max_retries = 3
        retry_delay = 2
        
        for attempt in range(max_retries):
            try:
                logger.info(f"DeepSeek API call attempt {attempt + 1}/{max_retries}")
                response = await self._call_deepseek_api(prompt, is_chat)
                if response:
                    elapsed_time = time.time() - start_time
                    logger.info(f"DeepSeek API call successful in {elapsed_time:.2f} seconds")
                    return response
                    
            except Exception as e:
                elapsed_time = time.time() - start_time
                logger.error(f"DeepSeek API error on attempt {attempt + 1} after {elapsed_time:.2f} seconds: {e}")
                if attempt < max_retries - 1:
                    delay = retry_delay * (2 ** attempt)
                    logger.info(f"Retrying in {delay} seconds...")
                    await asyncio.sleep(delay)
                else:
                    raise
        
        return None
    
    async def _call_deepseek_api(self, prompt: str, is_chat: bool = False) -> Dict[str, Any]:
        """Make actual API call to DeepSeek using SDK"""
        try:
            # Use SDK for API call
            response = self.client.chat.completions.create(
                model=self.model,
                messages=[{"role": "user", "content": prompt}],
                max_tokens=1000,
                temperature=0.7
            )
            
            # Convert SDK response to dict-like structure
            response_dict = {
                'choices': [
                    {
                        'message': {
                            'content': response.choices[0].message.content
                        }
                    }
                ] if response.choices else []
            }
            logger.info("DeepSeek API call successful via SDK")
            return response_dict
                        
        except Exception as e:
            logger.error(f"Error calling DeepSeek API via SDK: {e}")
            return None
    
    def _get_mock_recommendations(self, max_recommendations: int) -> Dict[str, Any]:
        """Return mock recommendations when API is not available"""
        return {
        "reasoning": "Based on your preferences for action RPGs and story-driven games, I've selected these titles that match your interests. All games have high ratings and are available on PC and major consoles.",
        "recommendations": [
                    {
                        "title": "Cyberpunk 2077",
                        "genre": "RPG",
                        "description": "Open-world action RPG set in Night City",
                        "why_recommended": "Matches your preference for action games with deep storytelling",
                        "platforms": ["PC", "PS4", "PS5", "Xbox One", "Xbox Series X"],
                        "rating": 8.5,
                        "release_year": "2020"
                    },
                    {
                        "title": "The Witcher 3: Wild Hunt",
                        "genre": "RPG",
                        "description": "Epic fantasy RPG with monster hunting",
                        "why_recommended": "Excellent action RPG with rich world and engaging combat",
                        "platforms": ["PC", "PS4", "PS5", "Xbox One", "Xbox Series X", "Nintendo Switch"],
                        "rating": 9.3,
                        "release_year": "2015"
                    },
                    {
                        "title": "Elden Ring",
                        "genre": "Action RPG",
                        "description": "Open-world action RPG with challenging combat",
                        "why_recommended": "Epic open-world game with deep combat mechanics",
                        "platforms": ["PC", "PS4", "PS5", "Xbox One", "Xbox Series X"],
                        "rating": 9.5,
                        "release_year": "2022"
                    }
                    ,
                    {
                        "title": "Forza Horizon 5",
                        "genre": "Racing",
                        "description": "Open-world racing game set in Mexico with tons of events and cars",
                        "why_recommended": "Relaxing driving with lots of variety and freedom to explore",
                        "platforms": ["PC", "Xbox One", "Xbox Series X"],
                        "rating": 9.0,
                        "release_year": "2021"
                    },
                    {
                        "title": "Hades",
                        "genre": "Roguelike Action",
                        "description": "Fast-paced action roguelike set in Greek mythology",
                        "why_recommended": "Highly replayable with great narrative and satisfying combat",
                        "platforms": ["PC", "PS4", "PS5", "Xbox One", "Xbox Series X", "Nintendo Switch"],
                        "rating": 9.2,
                        "release_year": "2020"
                    }
                ][:max_recommendations]
        }
    
    def _extract_recommendations_from_text(self, text: str, max_recommendations: int) -> List[Dict[str, Any]]:
        """Extract game recommendations from text response when JSON parsing fails"""
        try:
            recommendations = []
            
            # Simple pattern matching for common game recommendation formats
            lines = text.split('\n')
            current_game = {}
            
            for line in lines:
                line = line.strip()
                if not line:
                    continue
                
                # Look for game titles (usually start with numbers or dashes)
                if re.match(r'^[\d\-\.]+\.?\s*([A-Z][^:]+)', line):
                    if current_game and len(recommendations) < max_recommendations:
                        recommendations.append(current_game)
                    
                    title_match = re.match(r'^[\d\-\.]+\.?\s*([A-Z][^:]+)', line)
                    if title_match:
                        current_game = {
                            'title': title_match.group(1).strip(),
                            'genre': 'Unknown',
                            'description': '',
                            'why_recommended': '',
                            'platforms': [],
                            'rating': 0.0,
                            'release_year': ''
                        }
                
                # Look for genre information
                elif 'genre' in line.lower() or 'type' in line.lower():
                    if current_game:
                        current_game['genre'] = line.split(':')[-1].strip()
                
                # Look for description
                elif 'description' in line.lower() or 'about' in line.lower():
                    if current_game:
                        current_game['description'] = line.split(':')[-1].strip()
                
                # Look for platforms
                elif any(platform in line.lower() for platform in ['pc', 'ps', 'xbox', 'switch', 'nintendo']):
                    if current_game:
                        platforms = []
                        for platform in ['PC', 'PS4', 'PS5', 'Xbox One', 'Xbox Series X', 'Nintendo Switch']:
                            if platform.lower() in line.lower():
                                platforms.append(platform)
                        if platforms:
                            current_game['platforms'] = platforms
            
            # Add the last game if exists
            if current_game and len(recommendations) < max_recommendations:
                recommendations.append(current_game)
            
            # Fill with mock data if not enough recommendations found
            if len(recommendations) < max_recommendations:
                existing_titles = {
                    rec.get('title')
                    for rec in recommendations
                    if isinstance(rec, dict) and isinstance(rec.get('title'), str)
                }

                mock_response = self._get_mock_recommendations(max_recommendations)
                mock_games = mock_response.get('recommendations', [])

                for mock in mock_games:
                    if len(recommendations) >= max_recommendations:
                        break

                    title = mock.get('title')
                    if isinstance(title, str) and title in existing_titles:
                        continue

                    recommendations.append(mock)
                    if isinstance(title, str):
                        existing_titles.add(title)
            
            logger.info(f"Extracted {len(recommendations)} recommendations from text")
            return recommendations[:max_recommendations]
            
        except Exception as e:
            logger.error(f"Error extracting recommendations from text: {e}")
            return []
    
    async def is_available(self) -> bool:
        """Check if DeepSeek service is available"""
        if not self.api_key or not self.client:
            return False
        
        # Check circuit breaker status
        if self._is_circuit_open():
            return False
            
        return True
    
    def get_circuit_breaker_status(self) -> Dict[str, Any]:
        """Get circuit breaker status for monitoring"""
        return {
            "circuit_open": self.circuit_open,
            "failure_count": self.failure_count,
            "last_failure_time": self.last_failure_time,
            "circuit_open_timeout": self.circuit_open_timeout,
            "api_key_configured": bool(self.api_key)
        }
