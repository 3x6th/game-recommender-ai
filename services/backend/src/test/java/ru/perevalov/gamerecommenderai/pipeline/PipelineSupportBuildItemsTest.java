package ru.perevalov.gamerecommenderai.pipeline;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import ru.perevalov.gamerecommenderai.dto.GameRecommendation;
import ru.perevalov.gamerecommenderai.message.dto.MessageCardDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageItemDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageReasoningItemDto;

/**
 * Контрактный инвариант PCAI-141 follow-up: {@code buildItems} собирает
 * полиморфный список {@code items[]} (reasoning-блок первым, затем карточки игр).
 * Поля {@link GameRecommendation} переезжают в {@link MessageCardDto} 1-в-1.
 */
class PipelineSupportBuildItemsTest {

    private final PipelineSupport support = new PipelineSupport();

    @Test
    void buildItems_whenNothing_thenEmpty() {
        assertThat(support.buildItems(null, null)).isEmpty();
        assertThat(support.buildItems("", Collections.emptyList())).isEmpty();
        assertThat(support.buildItems("   ", Collections.emptyList())).isEmpty();
    }

    @Test
    void buildItems_whenOnlyReasoning_thenSingleReasoningItem() {
        List<MessageItemDto> items = support.buildItems(
                "Подобрал расслабляющие гонки",
                null
        );

        assertThat(items).hasSize(1);
        assertThat(items.get(0)).isInstanceOf(MessageReasoningItemDto.class);
        assertThat(((MessageReasoningItemDto) items.get(0)).getText())
                .isEqualTo("Подобрал расслабляющие гонки");
    }

    @Test
    void buildItems_whenOnlyRecommendations_thenOnlyCardsNoReasoning() {
        GameRecommendation rec = GameRecommendation.builder()
                .title("Hades")
                .genre("Roguelike")
                .description("Greek-myth roguelike")
                .whyRecommended("Snappy short runs")
                .platforms(List.of("PC"))
                .build();

        List<MessageItemDto> items = support.buildItems(null, List.of(rec));

        assertThat(items).hasSize(1);
        assertThat(items.get(0)).isInstanceOf(MessageCardDto.class);
        assertThat(((MessageCardDto) items.get(0)).getTitle()).isEqualTo("Hades");
    }

    @Test
    void buildItems_whenBoth_thenReasoningFirstThenCards_orderPreserved() {
        GameRecommendation a = GameRecommendation.builder()
                .title("A")
                .genre("g1")
                .description("d1")
                .whyRecommended("w1")
                .platforms(List.of("PC"))
                .build();
        GameRecommendation b = GameRecommendation.builder()
                .title("B")
                .genre("g2")
                .description("d2")
                .whyRecommended("w2")
                .platforms(List.of("PS5"))
                .build();

        List<MessageItemDto> items = support.buildItems(
                "Подобрал A и B под запрос",
                Arrays.asList(a, b)
        );

        assertThat(items).hasSize(3);
        assertThat(items.get(0)).isInstanceOf(MessageReasoningItemDto.class);
        assertThat(items.get(1)).isInstanceOf(MessageCardDto.class);
        assertThat(items.get(2)).isInstanceOf(MessageCardDto.class);
        assertThat(((MessageCardDto) items.get(1)).getTitle()).isEqualTo("A");
        assertThat(((MessageCardDto) items.get(2)).getTitle()).isEqualTo("B");
    }

    @Test
    void buildItems_whenFullRecommendation_thenAllFieldsCopied() {
        GameRecommendation rec = GameRecommendation.builder()
                .title("Forza Horizon 5")
                .genre("Racing, Open World")
                .description("Open-world racing in Mexico")
                .whyRecommended("Short relaxing sessions match your context")
                .platforms(Arrays.asList("PC", "Xbox Series X/S"))
                .rating(9.2)
                .releaseYear("2021")
                .build();

        List<MessageItemDto> items = support.buildItems(null, List.of(rec));
        MessageCardDto card = (MessageCardDto) items.get(0);

        assertThat(card.getTitle()).isEqualTo("Forza Horizon 5");
        assertThat(card.getGenre()).isEqualTo("Racing, Open World");
        assertThat(card.getDescription()).isEqualTo("Open-world racing in Mexico");
        assertThat(card.getWhyRecommended()).isEqualTo("Short relaxing sessions match your context");
        assertThat(card.getPlatforms()).containsExactly("PC", "Xbox Series X/S");
        assertThat(card.getRating()).isEqualTo(9.2);
        assertThat(card.getReleaseYear()).isEqualTo("2021");
    }
}
