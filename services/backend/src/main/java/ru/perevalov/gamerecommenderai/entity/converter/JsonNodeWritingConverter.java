package ru.perevalov.gamerecommenderai.entity.converter;

import com.fasterxml.jackson.databind.JsonNode;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

@Component
@WritingConverter
public class JsonNodeWritingConverter implements Converter<JsonNode, Json> {

    @Override
    public Json convert(JsonNode source) {
        return Json.of(source.toString());
    }
}
