package ru.perevalov.gamerecommenderai.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.dto.chat.ProceedResponse;
import ru.perevalov.gamerecommenderai.entity.ChatMessage;
import ru.perevalov.gamerecommenderai.security.RequestIdentity;
import ru.perevalov.gamerecommenderai.service.RequestContext;

/**
 * Контекст выполнения recommendation pipeline.
 */
@Getter
@Setter
public class PipelineContext {
    private final PipelineInput input;

    private UUID clientRequestId;
    private UUID requestedChatId;
    private List<String> tags;
    private RequestIdentity requestIdentity;
    private RequestContext userContext;
    private UUID chatId;
    private boolean duplicate;
    private UUID userMessageId;
    private UUID assistantMessageId;
    private GameRecommendationResponse response;
    private String errorMessage;

    /**
     * Сохраненные за этот ход сообщения ассистента (entity со всеми полями:
     * id, createdAt, meta-envelope). Будут отданы наружу как
     * {@code ProceedResponse.messages[]} через {@code ChatMapper}.
     * <p>
     * USER-сообщение здесь не сохраняем: FE его и так знает, и эхо в ответе
     * /proceed нам не нужно (см. {@code contracts/docs/api-contract.md} §1).
     */
    private List<ChatMessage> assistantMessages = new ArrayList<>();

    /**
     * Финальный ответ /proceed, собранный {@code ResponseStep}-ом.
     */
    private ProceedResponse proceedResponse;

    /**
     * Создает контекст обработки запроса.
     *
     * @param request входной запрос
     * @param clientRequestIdHeader значение заголовка X-Client-Request-Id
     */
    public PipelineContext(GameRecommendationRequest request, String clientRequestIdHeader) {
        this.input = new PipelineInput(request, clientRequestIdHeader);
    }

    /**
     * Возвращает исходный запрос клиента.
     *
     * @return исходный запрос
     */
    public GameRecommendationRequest getRequest() {
        return input.getRequest();
    }

    /**
     * Возвращает значение заголовка X-Client-Request-Id.
     *
     * @return значение заголовка или {@code null}
     */
    public String getClientRequestIdHeader() {
        return input.getClientRequestIdHeader();
    }
}
