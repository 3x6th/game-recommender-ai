package ru.perevalov.gamerecommenderai.pipeline;

import reactor.core.publisher.Mono;

/**
 * Шаг recommendation pipeline.
 */
public interface PipelineStep {
    /**
     * Выполняет шаг pipeline и возвращает обновленный контекст.
     *
     * @param context контекст обработки
     * @return обновленный контекст
     */
    Mono<PipelineContext> handle(PipelineContext context);
}
