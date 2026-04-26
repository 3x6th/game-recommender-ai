package ru.perevalov.gamerecommenderai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.constant.ChatLimits;

/**
 * Запрос на следующий шаг диалога с рекомендательным AI.
 * <p>
 * Поля содержат Bean Validation аннотации, описывающие контракт API
 * (длины, обязательность). Сами проверки применяются императивно в
 * {@code ContextResolverStep}, который остаётся источником правды для лимитов
 * (см. {@link ChatLimits}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameRecommendationRequest {

    @NotBlank
    @Size(max = ChatLimits.MAX_CONTENT_LENGTH)
    private String content;

    @Size(max = ChatLimits.MAX_TAGS)
    private String[] tags;

    @Size(max = ChatLimits.MAX_ID_LENGTH)
    private String steamId;

    @Size(max = ChatLimits.MAX_ID_LENGTH)
    private String chatId;

    @Size(max = ChatLimits.MAX_ID_LENGTH)
    private String clientRequestId;
}
