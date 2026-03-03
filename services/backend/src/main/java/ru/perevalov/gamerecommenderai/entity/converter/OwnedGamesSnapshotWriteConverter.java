package ru.perevalov.gamerecommenderai.entity.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.entity.embedded.OwnedGamesSnapshot;

@Slf4j
@WritingConverter
@Component
@RequiredArgsConstructor
public class OwnedGamesSnapshotWriteConverter implements Converter<OwnedGamesSnapshot, Json> {

    private final ObjectMapper objectMapper;

    @Override
    public Json convert(OwnedGamesSnapshot source) {
        try {
            return Json.of(objectMapper.writeValueAsString(source));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize OwnedGamesSnapshot to JSON", e);
            return null;
        }
    }
}
