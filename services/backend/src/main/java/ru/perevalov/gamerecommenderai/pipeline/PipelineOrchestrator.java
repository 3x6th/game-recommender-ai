package ru.perevalov.gamerecommenderai.pipeline;

import java.util.Comparator;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.chat.ProceedResponse;
import ru.perevalov.gamerecommenderai.pipeline.step.ResponseStep;

/**
 * Оркестратор обработки запроса через recommendation pipeline.
 */
@Service
@RequiredArgsConstructor
public class PipelineOrchestrator {
    private final List<PipelineStep> steps;

    /**
     * Сортирует шаги один раз после создания бина.
     */
    @PostConstruct
    void init() {
        steps.sort(Comparator.comparingInt(this::resolveOrder));
    }

    /**
     * Выполняет pipeline и возвращает итоговый ответ.
     *
     * @param context контекст обработки
     * @return итоговый ответ recommendation pipeline
     */
    public Mono<ProceedResponse> handle(PipelineContext context) {
        Mono<PipelineContext> chain = Mono.just(context);
        for (PipelineStep step : steps) {
            chain = chain.flatMap(current -> shouldSkip(current, step)
                    ? Mono.just(current)
                    : step.handle(current));
        }
        return chain.map(PipelineContext::getProceedResponse);
    }

    /**
     * Возвращает порядок шага для сортировки pipeline.
     *
     * @param step шаг pipeline
     * @return порядок выполнения
     */
    private int resolveOrder(PipelineStep step) {
        if (step instanceof Ordered ordered) {
            return ordered.getOrder();
        }
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * Определяет, нужно ли пропустить шаг после soft-failure AI.
     *
     * @param context текущий контекст
     * @param step текущий шаг pipeline
     * @return {@code true}, если шаг нужно пропустить
     */
    private boolean shouldSkip(PipelineContext context, PipelineStep step) {
        if (context.getErrorMessage() == null) {
            return false;
        }
        return !(step instanceof ResponseStep);
    }
}
