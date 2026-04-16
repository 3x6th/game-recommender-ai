package ru.perevalov.gamerecommenderai.pipeline.step;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.pipeline.PipelineContext;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStep;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStepOrder;
import ru.perevalov.gamerecommenderai.pipeline.PipelineSupport;
import ru.perevalov.gamerecommenderai.security.RequestIdentity;
import ru.perevalov.gamerecommenderai.service.RequestContext;
import ru.perevalov.gamerecommenderai.service.UserService;

/**
 * Определяет начальный контекст запроса для pipeline.
 * Шаг валидирует пользовательский ввод, выбирает итоговый clientRequestId,
 * извлекает теги и chatId, а также формирует пользовательский или гостевой контекст.
 */
@Component
@RequiredArgsConstructor
public class ContextResolverStep implements PipelineStep, Ordered {
    private static final int MAX_CONTENT_LENGTH = 2000;

    private final UserService userService;
    private final PipelineSupport support;

    /**
     * Заполняет базовый контекст pipeline: {@code clientRequestId}, запрошенный chatId,
     * извлечённые теги, идентичность запроса и owner-контекст для пользователя или гостевой сессии.
     *
     * @param context текущий контекст pipeline
     * @return обновлённый контекст pipeline
     */
    @Override
    public Mono<PipelineContext> handle(PipelineContext context) {
        validateContent(context);
        context.setClientRequestId(resolveClientRequestId(context));
        context.setRequestedChatId(support.parseUuid(context.getRequest().getChatId()));
        context.setTags(support.extractTags(context.getRequest()));

        return Mono.deferContextual(ctxView -> {
            RequestIdentity identity = ctxView.getOrDefault(RequestIdentity.class, RequestIdentity.anonymous());
            context.setRequestIdentity(identity);

            Long steamId = resolveSteamId(context, identity);
            if (steamId != null) {
                return userService.createIfNotExists(steamId)
                        .map(user -> {
                            context.setUserContext(RequestContext.forUser(user.getId(), steamId, null));
                            return context;
                        });
            }

            String sessionId = identity.sessionId();
            if (sessionId != null && !sessionId.isBlank()) {
                context.setUserContext(RequestContext.forGuest(sessionId, null));
                return Mono.just(context);
            }

            return Mono.error(new GameRecommenderException(
                    ErrorType.INVALID_REQUEST_CONTEXT, "steamId or sessionId is required"));
        });
    }

    /**
     * Возвращает порядок выполнения этого шага в pipeline.
     *
     * @return константа порядка выполнения шага
     */
    @Override
    public int getOrder() {
        return PipelineStepOrder.CONTEXT_RESOLVER;
    }

    /**
     * Определяет итоговый clientRequestId с приоритетом:
     * request body -> заголовок {@code X-Client-Request-Id} -> сгенерированный UUID.
     *
     * @param context текущий контекст pipeline
     * @return итоговый clientRequestId для запроса
     */
    private UUID resolveClientRequestId(PipelineContext context) {
        UUID fromBody = support.parseUuid(context.getRequest().getClientRequestId());
        if (fromBody != null) {
            return fromBody;
        }

        UUID fromHeader = support.parseUuid(context.getClientRequestIdHeader());
        if (fromHeader != null) {
            return fromHeader;
        }

        return UUID.randomUUID();
    }

    /**
     * Определяет {@code steamId} сначала из тела запроса, затем из аутентифицированной identity.
     *
     * @param context текущий контекст pipeline
     * @param identity определённая идентичность запроса
     * @return steamId или {@code null}, если он отсутствует
     */
    private Long resolveSteamId(PipelineContext context, RequestIdentity identity) {
        Long fromRequest = support.parseLong(context.getRequest().getSteamId());
        if (fromRequest != null) {
            return fromRequest;
        }
        return identity != null ? identity.steamId() : null;
    }

    /**
     * Валидирует текст пользовательского сообщения до передачи запроса дальше по pipeline.
     *
     * @param context текущий контекст pipeline
     */
    private void validateContent(PipelineContext context) {
        String content = context.getRequest() != null ? context.getRequest().getContent() : null;
        if (content == null || content.isBlank()) {
            throw new GameRecommenderException(ErrorType.VALIDATION_ERROR, "content is blank");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new GameRecommenderException(
                    ErrorType.VALIDATION_ERROR,
                    "content exceeds " + MAX_CONTENT_LENGTH + " characters");
        }
    }
}
