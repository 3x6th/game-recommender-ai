package ru.perevalov.gamerecommenderai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.chat.ProceedResponse;
import ru.perevalov.gamerecommenderai.pipeline.PipelineContext;
import ru.perevalov.gamerecommenderai.pipeline.PipelineOrchestrator;

/**
 * Контроллер получения игровых рекомендаций.
 *
 * <p>Контракт ответа — {@link ProceedResponse}: {@code chatId + messages[]} с
 * полным meta-envelope у каждого сообщения. См. {@code contracts/docs/api-contract.md} §1.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameRecommendationController {

    private static final String CLIENT_REQUEST_ID_HEADER = "X-Client-Request-Id";

    private final PipelineOrchestrator orchestrator;

    /**
     * Обрабатывает шаг диалога и делегирует бизнес-логику recommendation pipeline.
     *
     * @param reqMono поток запроса клиента
     * @param clientRequestIdHeader идентификатор запроса клиента для идемпотентности
     * @return HTTP-ответ с chatId и сообщениями текущего хода
     */
    @PostMapping("/proceed")
    public Mono<ResponseEntity<ProceedResponse>> getRecommendations(
            @RequestBody Mono<GameRecommendationRequest> reqMono,
            @RequestHeader(value = CLIENT_REQUEST_ID_HEADER, required = false) String clientRequestIdHeader
    ) {
        return reqMono
                .flatMap(req -> orchestrator.handle(new PipelineContext(req, clientRequestIdHeader)))
                .doOnNext(resp -> log.info("Returning /proceed response with {} messages",
                        resp.getMessages() != null ? resp.getMessages().size() : 0))
                .map(ResponseEntity::ok);
    }
}
