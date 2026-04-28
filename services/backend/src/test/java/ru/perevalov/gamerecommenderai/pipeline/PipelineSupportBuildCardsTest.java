package ru.perevalov.gamerecommenderai.pipeline;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.perevalov.gamerecommenderai.dto.GameRecommendation;
import ru.perevalov.gamerecommenderai.message.dto.MessageCardDto;

/**
 * Контрактный инвариант PCAI-141: маппинг {@link GameRecommendation} →
 * {@link MessageCardDto} обязан быть 1-в-1 и не терять поля. До правки
 * pipeline терял genre/description/platforms/releaseYear, что превращало
 * историю чата в пустые карточки.
 */
class PipelineSupportBuildCardsTest {

    private final PipelineSupport support = new PipelineSupport(new ObjectMapper());

    @Test
    void buildCards_whenItemsNull_thenEmptyList() {
        assertThat(support.buildCards(null)).isEmpty();
    }

    @Test
    void buildCards_whenItemsEmpty_thenEmptyList() {
        assertThat(support.buildCards(Collections.emptyList())).isEmpty();
    }

    @Test
    void buildCards_whenFullRecommendation_thenAllFieldsCopied() {
        GameRecommendation rec = GameRecommendation.builder()
                .title("Forza Horizon 5")
                .genre("Racing, Open World")
                .description("Open-world racing in Mexico")
                .whyRecommended("Short relaxing sessions match your context")
                .platforms(Arrays.asList("PC", "Xbox Series X/S"))
                .rating(9.2)
                .releaseYear("2021")
                .build();

        List<MessageCardDto> cards = support.buildCards(List.of(rec));

        assertThat(cards).hasSize(1);
        MessageCardDto card = cards.get(0);

        assertThat(card.getTitle()).isEqualTo("Forza Horizon 5");
        assertThat(card.getGenre()).isEqualTo("Racing, Open World");
        assertThat(card.getDescription()).isEqualTo("Open-world racing in Mexico");
        assertThat(card.getWhyRecommended()).isEqualTo("Short relaxing sessions match your context");
        assertThat(card.getPlatforms()).containsExactly("PC", "Xbox Series X/S");
        assertThat(card.getRating()).isEqualTo(9.2);
        assertThat(card.getReleaseYear()).isEqualTo("2021");
    }

    @Test
    void buildCards_whenOptionalFieldsNull_thenNotIncludedButRequiredKept() {
        GameRecommendation rec = GameRecommendation.builder()
                .title("Hades")
                .genre("Roguelike")
                .description("Greek-myth roguelike")
                .whyRecommended("Snappy short runs")
                .platforms(List.of("PC"))
                .build();

        List<MessageCardDto> cards = support.buildCards(List.of(rec));

        assertThat(cards).hasSize(1);
        MessageCardDto card = cards.get(0);

        assertThat(card.getTitle()).isEqualTo("Hades");
        assertThat(card.getRating()).isNull();
        assertThat(card.getReleaseYear()).isNull();
        assertThat(card.getTags()).isNull();
        assertThat(card.getMatchScore()).isNull();
    }

    @Test
    void buildCards_whenMultipleItems_thenPreservesOrderAndAllFields() {
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

        List<MessageCardDto> cards = support.buildCards(List.of(a, b));

        assertThat(cards).hasSize(2);
        assertThat(cards.get(0).getTitle()).isEqualTo("A");
        assertThat(cards.get(1).getTitle()).isEqualTo("B");
        assertThat(cards.get(0).getGenre()).isEqualTo("g1");
        assertThat(cards.get(1).getGenre()).isEqualTo("g2");
    }
}
