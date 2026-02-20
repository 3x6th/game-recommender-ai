package ru.perevalov.gamerecommenderai.exception;

import java.util.List;

import lombok.Getter;

@Getter
public class InvalidMetaException extends RuntimeException {
    private final String code;
    private final List<String> errors;

    public InvalidMetaException(String code, List<String> errors) {
        super(buildMessage(code, errors));
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    private static String buildMessage(String code, List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "Invalid message meta: " + code;
        }
        return "Invalid message meta: " + code + ". Errors: " + String.join("; ", errors);
    }
}
