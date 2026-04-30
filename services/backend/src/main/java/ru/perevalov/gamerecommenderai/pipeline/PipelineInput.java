package ru.perevalov.gamerecommenderai.pipeline;

import lombok.Getter;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;

/**
 * Неизменяемые входные данные recommendation pipeline.
 */
@Getter
public class PipelineInput {
    private final GameRecommendationRequest request;
    private final String clientRequestIdHeader;

    /**
     * Создает неизменяемый набор входных данных pipeline.
     *
     * @param request исходный запрос клиента
     * @param clientRequestIdHeader значение заголовка X-Client-Request-Id
     */
    public PipelineInput(GameRecommendationRequest request, String clientRequestIdHeader) {
        this.request = request;
        this.clientRequestIdHeader = clientRequestIdHeader;
    }
}
