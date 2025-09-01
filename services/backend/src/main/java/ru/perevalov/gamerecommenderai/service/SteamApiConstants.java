package ru.perevalov.gamerecommenderai.service;

/**
 * Константы для Steam API сервиса
 */
public enum SteamApiConstants {
    
    // Resilience4j константы
    STEAM_API_CIRCUIT_BREAKER("steamApi"),
    STEAM_API_RETRY("steamApi"),
    STEAM_API_TIME_LIMITER("steamApi"),
    
    // Кэш константы
    STEAM_DATA_CACHE("steamData"),
    
    // Ключи кэша
    USER_GAMES_CACHE_KEY_PREFIX("user_games_"),
    GAME_RECOMMENDATIONS_CACHE_KEY_PREFIX("game_recommendations_");
    
    private final String value;
    
    SteamApiConstants(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    @Override
    public String toString() {
        return value;
    }
    
    // Статические константы для использования в аннотациях
    public static final String STEAM_API_CIRCUIT_BREAKER_VALUE = "steamApi";
    public static final String STEAM_API_RETRY_VALUE = "steamApi";
    public static final String STEAM_API_TIME_LIMITER_VALUE = "steamApi";
    public static final String STEAM_DATA_CACHE_VALUE = "steamData";
    public static final String USER_GAMES_CACHE_KEY_PREFIX_VALUE = "user_games_";
    public static final String GAME_RECOMMENDATIONS_CACHE_KEY_PREFIX_VALUE = "game_recommendations_";
}
