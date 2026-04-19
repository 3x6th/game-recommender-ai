package ru.perevalov.gamerecommenderai.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Конфигурация gRPC Internal Tools API (PCAI-122): дефолтные/максимальные лимиты и таймаут
 * обращения к внешнему Steam Store при обработке {@code JavaToolsService.*} RPC.
 * <p>
 * Вынесена из {@code app.recommender.prompt.top-by-playtime-list-size}, чтобы семантика
 * «лимит для AI-промпта» и «лимит результатов gRPC-инструмента» не делили один ключ.
 */
@ConfigurationProperties(prefix = "app.grpc.tools")
@Validated
public record GrpcToolsProps(
        @Min(1) int defaultLimit,
        @Min(1) int maxLimit,
        @Min(1) long steamFetchTimeoutSeconds
) {

    /**
     * Нормализует пришедший из запроса {@code limit}: если 0 (дефолт proto int32)
     * или выходит за допустимый диапазон — возвращает {@link #defaultLimit()}.
     */
    public int clampLimit(int requested) {
        if (requested <= 0) {
            return defaultLimit;
        }
        return Math.min(requested, maxLimit);
    }
}
