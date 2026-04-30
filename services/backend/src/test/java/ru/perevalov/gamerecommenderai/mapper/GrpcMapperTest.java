package ru.perevalov.gamerecommenderai.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.perevalov.gamerecommenderai.dto.steam.SteamGameDetailsResponseDto;
import ru.perevalov.gamerecommenderai.dto.steam.SteamGameDetailsResponseDto.SteamGameDataResponseDto;
import ru.perevalov.gamerecommenderai.dto.steam.SteamGameDetailsResponseDto.SteamGenreResponseDto;
import ru.perevalov.gamerecommenderai.grpc.SimilarGamesResponse;
import ru.perevalov.gamerecommenderai.grpc.SteamAppResponse;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Регресс-тесты на null-safety и семантику маппинга в {@link GrpcMapper} — та часть,
 * на которой падает {@code JavaToolsServiceImpl} при походе в Steam Store в рамках
 * gRPC Internal Tools API (PCAI-122).
 */
class GrpcMapperTest {

    private final GrpcMapper mapper = new GrpcMapper();

    @Test
    @DisplayName("toSteamAppResponse(DTO) → empty при null")
    void shouldReturnEmptyWhenDtoIsNull() {
        Optional<SteamAppResponse> result = mapper.toSteamAppResponse((SteamGameDetailsResponseDto) null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("toSteamAppResponse(DTO) → empty при success=false")
    void shouldReturnEmptyWhenSuccessIsFalse() {
        var dto = SteamGameDetailsResponseDto.builder()
                                             .appId("730")
                                             .success(false)
                                             .steamGameDataResponseDto(
                                                     dtoWith(730, "CS", "short", "long", List.of())
                                                             .steamGameDataResponseDto())
                                             .build();

        assertThat(mapper.toSteamAppResponse(dto)).isEmpty();
    }

    @Test
    @DisplayName("toSteamAppResponse(DTO) → empty при success=true но data=null")
    void shouldReturnEmptyWhenDataIsNull() {
        var dto = SteamGameDetailsResponseDto.builder()
                                             .appId("730")
                                             .success(true)
                                             .steamGameDataResponseDto(null)
                                             .build();

        assertThat(mapper.toSteamAppResponse(dto)).isEmpty();
    }

    @Test
    @DisplayName("toSteamAppResponse(DTO) → описание берётся из short, когда оно есть")
    void shouldPreferShortDescription() {
        var dto = dtoWith(730, "Counter-Strike 2", "short", "long",
                List.of(new SteamGenreResponseDto("1", "Action")));

        SteamAppResponse response = mapper.toSteamAppResponse(dto).orElseThrow();

        assertThat(response.getAppId()).isEqualTo(730);
        assertThat(response.getName()).isEqualTo("Counter-Strike 2");
        assertThat(response.getDescription()).isEqualTo("short");
        assertThat(response.getGenresList()).containsExactly("Action");
    }

    @Test
    @DisplayName("toSteamAppResponse(DTO) → fallback на detailedDescription, если short пустой")
    void shouldFallBackToDetailedDescription() {
        var dto = dtoWith(730, "Counter-Strike 2", "", "Long description",
                List.of(new SteamGenreResponseDto("1", "Action")));

        SteamAppResponse response = mapper.toSteamAppResponse(dto).orElseThrow();

        assertThat(response.getDescription()).isEqualTo("Long description");
    }

    @Test
    @DisplayName("toSteamAppResponse(DTO) → пустые/blank/null жанры отфильтровываются")
    void shouldFilterOutBlankGenres() {
        var dto = dtoWith(730, "CS", "short", "long", List.of(
                new SteamGenreResponseDto("1", "Action"),
                new SteamGenreResponseDto("2", ""),
                new SteamGenreResponseDto("3", null),
                new SteamGenreResponseDto("4", "Shooter")
        ));

        SteamAppResponse response = mapper.toSteamAppResponse(dto).orElseThrow();

        assertThat(response.getGenresList()).containsExactly("Action", "Shooter");
    }

    @Test
    @DisplayName("toSteamAppResponse(DTO) → не падает NPE, когда Steam вернул name=null")
    void shouldNotThrowWhenNameIsNull() {
        var dto = dtoWith(730, null, "short", "long", List.of());

        SteamAppResponse response = mapper.toSteamAppResponse(dto).orElseThrow();

        assertThat(response.getName()).isEmpty();
    }

    @Test
    @DisplayName("toSteamAppResponse(DTO) → не падает, когда жанры = null")
    void shouldNotThrowWhenGenresAreNull() {
        var dto = dtoWith(730, "CS", "short", "long", null);

        SteamAppResponse response = mapper.toSteamAppResponse(dto).orElseThrow();

        assertThat(response.getGenresList()).isEmpty();
    }

    @Test
    @DisplayName("toSteamAppResponse(long,String) → null-safe для name + exact overflow check для appId")
    void shouldBuildLightweightResponseSafely() {
        SteamAppResponse response = mapper.toSteamAppResponse(730L, null);

        assertThat(response.getAppId()).isEqualTo(730);
        assertThat(response.getName()).isEmpty();
        assertThat(response.getDescription()).isEmpty();
        assertThat(response.getGenresList()).isEmpty();

        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> mapper.toSteamAppResponse((long) Integer.MAX_VALUE + 1L, "overflow"));
    }

    @Test
    @DisplayName("toSimilarGamesResponse → пустой билд при null/empty списке")
    void shouldReturnEmptySimilarGamesWhenNoCandidates() {
        SimilarGamesResponse fromNull = mapper.toSimilarGamesResponse(null);
        SimilarGamesResponse fromEmpty = mapper.toSimilarGamesResponse(List.of());

        assertThat(fromNull.getGamesList()).isEmpty();
        assertThat(fromEmpty.getGamesList()).isEmpty();
    }

    @Test
    @DisplayName("toSimilarGamesResponse → собирает все игры в ответ")
    void shouldAggregateSimilarGames() {
        var first = mapper.toSteamAppResponse(1L, "Game A");
        var second = mapper.toSteamAppResponse(2L, "Game B");

        SimilarGamesResponse response = mapper.toSimilarGamesResponse(List.of(first, second));

        assertThat(response.getGamesList()).containsExactly(first, second);
    }

    private static SteamGameDetailsResponseDto dtoWith(int appId,
                                                       String name,
                                                       String shortDescription,
                                                       String detailedDescription,
                                                       List<SteamGenreResponseDto> genres) {
        return SteamGameDetailsResponseDto.builder()
                                          .appId(String.valueOf(appId))
                                          .success(true)
                                          .steamGameDataResponseDto(new SteamGameDataResponseDto(
                                                  "game", name, appId, 0, false,
                                                  List.of(), detailedDescription, null, shortDescription,
                                                  null, null, null, null, null, null,
                                                  null, null, null, null, null, null,
                                                  null, genres, null))
                                          .build();
    }

}
