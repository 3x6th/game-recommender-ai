package ru.perevalov.gamerecommenderai.mapper;

import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.dto.AiContextRequest;
import ru.perevalov.gamerecommenderai.dto.steam.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.grpc.FullAiContextRequestProto;
import ru.perevalov.gamerecommenderai.grpc.GameProto;
import ru.perevalov.gamerecommenderai.grpc.ResponseProto;
import ru.perevalov.gamerecommenderai.grpc.SteamOwnedGamesResponseProto;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
            builder.addAllSelectedTags(Arrays.asList(dto.getSelectedTags()));
        }

        if (dto.getUserSteamLibrary() != null) {
            builder.addAllUserSteamLibrary(dto.getUserSteamLibrary());
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
                    .collect(Collectors.toList());
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

}
