package ru.perevalov.gamerecommenderai.message;

import java.util.List;

import lombok.Getter;

@Getter
public class MessageMetaValidationResult {
    private final List<String> errors;
    private final List<String> warnings;

    public MessageMetaValidationResult(List<String> errors, List<String> warnings) {
        this.errors = List.copyOf(errors);
        this.warnings = List.copyOf(warnings);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
