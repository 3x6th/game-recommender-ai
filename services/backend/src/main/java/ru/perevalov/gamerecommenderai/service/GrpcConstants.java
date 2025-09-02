package ru.perevalov.gamerecommenderai.service;

/**
 * Константы для gRPC взаимодействия и кэширования.
 * Используются в аннотациях как compile-time константы.
 */
public final class GrpcConstants {

    private GrpcConstants() {}

    // Resilience4j instance name
    public static final String GRPC_CLIENT = "grpcClient";

    // Cache names
    public static final String CACHE_GAME_RECOMMENDATIONS = "gameRecommendations";
    public static final String CACHE_USER_PREFERENCES = "userPreferences";
}


