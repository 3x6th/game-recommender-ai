package ru.perevalov.gamerecommenderai.exception;

import lombok.Getter;

@Getter
public class GameRecommenderException extends RuntimeException {
    
    private final String errorCode;
    private final int httpStatus;
    
    public GameRecommenderException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    public GameRecommenderException(String message, String errorCode, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
} 