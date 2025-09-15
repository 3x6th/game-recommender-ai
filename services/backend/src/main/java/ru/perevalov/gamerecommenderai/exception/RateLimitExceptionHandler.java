package ru.perevalov.gamerecommenderai.exception;

import com.giffing.bucket4j.spring.boot.starter.context.RateLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class RateLimitExceptionHandler {

    @ExceptionHandler(value = {RateLimitException.class})
    protected ResponseEntity<?> handleRateLimit(RateLimitException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }

}
