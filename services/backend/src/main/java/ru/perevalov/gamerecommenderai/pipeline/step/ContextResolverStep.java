package ru.perevalov.gamerecommenderai.pipeline.step;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.constant.ChatLimits;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
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
        validateRequest(context);
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
     * Валидирует пользовательский ввод до передачи запроса дальше по pipeline.
     * <p>
     * Применяется как первый барьер защиты:
     * <ul>
     *     <li>{@code content} обязателен и не должен быть пустым;</li>
     *     <li>длина {@code content} ограничена {@link ChatLimits#MAX_CONTENT_LENGTH};</li>
     *     <li>массив {@code tags} ограничен по количеству и длине каждого элемента,
     *         чтобы запрос не разрастался произвольным «мусором».</li>
     * </ul>
     *
     * @param context текущий контекст pipeline
     */
    private void validateRequest(PipelineContext context) {
        GameRecommendationRequest request = context.getRequest();
        if (request == null) {
            throw new GameRecommenderException(ErrorType.VALIDATION_ERROR, "request body is required");
        }

        String content = request.getContent();
        if (content == null || content.isBlank()) {
            throw new GameRecommenderException(ErrorType.VALIDATION_ERROR, "content is blank");
        }
        if (content.length() > ChatLimits.MAX_CONTENT_LENGTH) {
            throw new GameRecommenderException(
                    ErrorType.VALIDATION_ERROR,
                    "content exceeds " + ChatLimits.MAX_CONTENT_LENGTH + " characters");
        }

        validateTags(request);
        validateIdLength("chatId", request.getChatId());
        validateIdLength("clientRequestId", request.getClientRequestId());
        validateIdLength("steamId", request.getSteamId());
    }

    /**
     * Валидирует массив тегов запроса по количеству и длине каждого тега.
     *
     * @param request запрос рекомендаций
     */
    private void validateTags(GameRecommendationRequest request) {
        String[] tags = request.getTags();
        if (tags == null) {
            return;
        }
        if (tags.length > ChatLimits.MAX_TAGS) {
            throw new GameRecommenderException(
                    ErrorType.VALIDATION_ERROR,
                    "tags count exceeds " + ChatLimits.MAX_TAGS);
        }
        for (String tag : tags) {
            if (tag != null && tag.length() > ChatLimits.MAX_TAG_LENGTH) {
                throw new GameRecommenderException(
                        ErrorType.VALIDATION_ERROR,
                        "tag length exceeds " + ChatLimits.MAX_TAG_LENGTH);
            }
        }
    }

    /**
     * Защищает строковые идентификаторы от чрезмерной длины.
     *
     * @param fieldName название поля для сообщения об ошибке
     * @param value     значение поля
     */
    private void validateIdLength(String fieldName, String value) {
        if (value != null && value.length() > ChatLimits.MAX_ID_LENGTH) {
            throw new GameRecommenderException(
                    ErrorType.VALIDATION_ERROR,
                    fieldName + " length exceeds " + ChatLimits.MAX_ID_LENGTH);
        }
    }
}
