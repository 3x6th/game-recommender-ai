package ru.perevalov.gamerecommenderai.mapper;

import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.dto.AiContextRequest;
import ru.perevalov.gamerecommenderai.dto.steam.SteamGameDetailsResponseDto;
import ru.perevalov.gamerecommenderai.dto.steam.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.grpc.FullAiContextRequestProto;
import ru.perevalov.gamerecommenderai.grpc.GameProto;
import ru.perevalov.gamerecommenderai.grpc.ResponseProto;
import ru.perevalov.gamerecommenderai.grpc.SimilarGamesResponse;
import ru.perevalov.gamerecommenderai.grpc.SteamAppResponse;
import ru.perevalov.gamerecommenderai.grpc.SteamOwnedGamesResponseProto;

import java.util.List;
import java.util.Optional;

@Component
public class GrpcMapper {

    public FullAiContextRequestProto toProto(AiContextRequest dto) {
        if (dto == null) {
            return FullAiContextRequestProto.getDefaultInstance();
        }

        FullAiContextRequestProto.Builder builder = FullAiContextRequestProto.newBuilder();

        if (dto.getUserMessage() != null) {
            builder.setUserMessage(dto.getUserMessage());
        }

        if (dto.getSelectedTags() != null) {
            builder.addAllSelectedTags(List.of(dto.getSelectedTags()));
        }

        if (dto.getProfileSummary() != null) {
            builder.setProfileSummary(dto.getProfileSummary());
        }

        if (dto.getChatId() != null) {
            builder.setChatId(dto.getChatId());
        }

        if (dto.getAgentId() != null) {
            builder.setAgentId(dto.getAgentId());
        }

        if (dto.getRequestId() != null) {
            builder.setRequestId(dto.getRequestId());
        }

        if (dto.getCorrelationId() != null) {
            builder.setCorrelationId(dto.getCorrelationId());
        }

        if (dto.getLanguage() != null) {
            builder.setLanguage(dto.getLanguage());
        }

        if (dto.getExcludeGenres() != null) {
            builder.addAllExcludeGenres(dto.getExcludeGenres());
        }


        builder.setMaxResults(dto.getMaxResults() > 0 ? dto.getMaxResults() : 10);

        return builder.build();

    }

    public SteamOwnedGamesResponseProto toProto(SteamOwnedGamesResponse dto) {
        if (dto == null || dto.getResponse() == null) {
            return SteamOwnedGamesResponseProto.getDefaultInstance();
        }

        return SteamOwnedGamesResponseProto.newBuilder()
                .setResponse(toProto(dto.getResponse()))
                .build();
    }

    public ResponseProto toProto(SteamOwnedGamesResponse.Response response) {
        if (response == null) {
            return ResponseProto.getDefaultInstance();
        }

        ResponseProto.Builder builder = ResponseProto.newBuilder()
                .setGameCount(response.getGameCount());

        if (response.getGames() != null) {
            List<GameProto> gameProtos = response.getGames().stream()
                    .map(this::toProto)
                    .toList();
            builder.addAllGames(gameProtos);
        }

        return builder.build();
    }

    public GameProto toProto(SteamOwnedGamesResponse.Game game) {
        if (game == null) {
            return GameProto.getDefaultInstance();
        }

        GameProto.Builder builder = GameProto.newBuilder()
                .setAppId(game.getAppId())
                .setPlaytime2Weeks(game.getPlaytime2weeks())
                .setPlaytimeForever(game.getPlaytimeForever());

        if (game.getName() != null) {
            builder.setName(game.getName());
        }

        return builder.build();
    }

    /**
     * Null-safe mapping ответа Steam Store в gRPC {@link SteamAppResponse}.
     * <p>
     * Когда Steam вернул {@code success=false} или пустое тело, возвращается
     * {@link Optional#empty()} — вызывающая сторона сама решит, как трактовать
     * (обычно {@code Status.NOT_FOUND}). Это лучше, чем молча отдавать пустой
     * «валидный» билд, из-за которого клиент считал бы appId найденным.
     *
     * @param response ответ {@code SteamStoreClient.fetchGameDetails}; может быть {@code null}
     * @return {@link Optional} с заполненным ответом или пустой, если маппить нечего
     */
    public Optional<SteamAppResponse> toSteamAppResponse(SteamGameDetailsResponseDto response) {
        if (response == null || !response.success() || response.steamGameDataResponseDto() == null) {
            return Optional.empty();
        }
        var data = response.steamGameDataResponseDto();

        String description = data.shortDescription();
        if (description == null || description.isBlank()) {
            description = data.detailedDescription();
        }

        List<String> genres = data.genres() == null
                ? List.of()
                : data.genres().stream()
                      .map(SteamGameDetailsResponseDto.SteamGenreResponseDto::description)
                      .filter(g -> g != null && !g.isBlank())
                      .toList();

        SteamAppResponse result = SteamAppResponse.newBuilder()
                .setAppId(data.steamAppid())
                .setName(Optional.ofNullable(data.name()).orElse(""))
                .setDescription(Optional.ofNullable(description).orElse(""))
                .addAllGenres(genres)
                .build();
        return Optional.of(result);
    }

    /**
     * Лёгкий маппинг для search-листингов (appId + name, без Steam Store call).
     */
    public SteamAppResponse toSteamAppResponse(long appId, String name) {
        return SteamAppResponse.newBuilder()
                .setAppId(Math.toIntExact(appId))
                .setName(name != null ? name : "")
                .setDescription("")
                .addAllGenres(List.of())
                .build();
    }

    public SimilarGamesResponse toSimilarGamesResponse(List<SteamAppResponse> games) {
        return SimilarGamesResponse.newBuilder()
                .addAllGames(games == null ? List.of() : games)
                .build();
    }

}
