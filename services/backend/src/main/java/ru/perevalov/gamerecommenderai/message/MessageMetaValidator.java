package ru.perevalov.gamerecommenderai.message;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import ru.perevalov.gamerecommenderai.dto.message.InvalidMetaException;

@Slf4j
@Component
public class MessageMetaValidator {
    private static final String INVALID_META_CODE = "META_INVALID";

    public void validateOrThrow(JsonNode meta) {
        MessageMetaValidationResult result = validate(meta);
        if (!result.isValid()) {
            throw new InvalidMetaException(INVALID_META_CODE, result.getErrors());
        }
        if (result.hasWarnings()) {
            log.warn("Message meta has warnings: {}", String.join("; ", result.getWarnings()));
        }
    }

    public MessageMetaValidationResult validate(JsonNode meta) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (meta == null) {
            errors.add("meta is null");
            return new MessageMetaValidationResult(errors, warnings);
        }

        if (!meta.isObject()) {
            errors.add("meta must be a JSON object");
            return new MessageMetaValidationResult(errors, warnings);
        }

        JsonNode schemaVersion = meta.get("schemaVersion");
        if (schemaVersion == null) {
            errors.add("schemaVersion is required");
        } else if (!schemaVersion.isInt()) {
            errors.add("schemaVersion must be an int");
        } else if (schemaVersion.asInt() < 1) {
            errors.add("schemaVersion must be >= 1");
        } else if (schemaVersion.asInt() != 1) {
            warnings.add("schemaVersion is not 1");
        }

        JsonNode type = meta.get("type");
        if (type == null) {
            errors.add("type is required");
        } else if (!type.isTextual()) {
            errors.add("type must be a string");
        } else if (type.asText().trim().isEmpty()) {
            errors.add("type must not be blank");
        } else {
            try {
                MessageMetaType.fromWireName(type.asText());
            } catch (IllegalArgumentException e) {
                warnings.add("Unknown meta.type: " + type.asText());
            }
        }

        JsonNode payload = meta.get("payload");
        if (payload == null || payload.isNull()) {
            errors.add("payload is required");
        } else if (!payload.isObject() && !payload.isArray()) {
            warnings.add("payload is not object/array");
        }

        JsonNode trace = meta.get("trace");
        if (trace != null) {
            if (!trace.isObject()) {
                warnings.add("trace must be an object");
            } else {
                JsonNode requestId = trace.get("requestId");
                if (requestId != null && !requestId.isTextual()) {
                    warnings.add("trace.requestId must be a string");
                }
                JsonNode runId = trace.get("runId");
                if (runId != null && !runId.isTextual()) {
                    warnings.add("trace.runId must be a string");
                }
            }
        }

        return new MessageMetaValidationResult(errors, warnings);
    }
}
