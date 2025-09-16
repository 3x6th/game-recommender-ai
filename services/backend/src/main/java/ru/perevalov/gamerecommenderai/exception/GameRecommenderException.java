package ru.perevalov.gamerecommenderai.exception;

import lombok.Getter;

@Getter
public class GameRecommenderException extends RuntimeException {
    private final ErrorType errorType;
    private final Object[] params;

    public GameRecommenderException(ErrorType errorType, Object... params) {
        super(String.format(errorType.getDescription(), params));
        this.errorType = errorType;
        this.params = params;
    }
} 