package ru.perevalov.gamerecommenderai.entity.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.entity.embedded.OwnedGamesSnapshot;

import java.io.IOException;

@Slf4j
@ReadingConverter
@Component
@RequiredArgsConstructor
public class OwnedGamesSnapshotReadConverter implements Converter<Json, OwnedGamesSnapshot> {

    private final ObjectMapper objectMapper;

    @Override
    public OwnedGamesSnapshot convert(Json source) {
        try {
            return objectMapper.readValue(source.asString(), OwnedGamesSnapshot.class);
        } catch (IOException e) {
            log.error("Failed to deserialize OwnedGamesSnapshot from JSON", e);
            return null;
        }
    }
}
