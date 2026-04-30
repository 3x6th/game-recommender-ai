package ru.perevalov.gamerecommenderai.mapper;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;
import reactor.core.Exceptions;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;

@Component
public class GrpcErrorMapper {

    public boolean isRetryableGrpcError(Throwable error) {
        Status.Code code = resolveStatusCode(unwrap(error));
        return code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED;
    }

    public Throwable mapGrpcError(Throwable error) {
        Throwable unwrapped = unwrap(error);

        if (unwrapped instanceof GameRecommenderException) {
            return unwrapped;
        }
        if (unwrapped instanceof CallNotPermittedException || isFallbackUnavailableGrpcError(unwrapped)) {
            return new GameRecommenderException(ErrorType.AI_SERVICE_UNAVAILABLE);
        }

        Status.Code statusCode = resolveStatusCode(unwrapped);
        if (statusCode != null) {
            return new GameRecommenderException(ErrorType.GRPC_COMMUNICATION_ERROR, statusCode);
        }

        return new GameRecommenderException(ErrorType.GRPC_COMMUNICATION_ERROR, unwrapped.getMessage());
    }

    private Status.Code resolveStatusCode(Throwable error) {
        if (error instanceof StatusRuntimeException statusRuntimeException) {
            return statusRuntimeException.getStatus().getCode();
        }
        if (error instanceof StatusException statusException) {
            return statusException.getStatus().getCode();
        }
        return null;
    }

    public String resolveFailureReason(Throwable error) {
        Throwable unwrapped = unwrap(error);
        if (unwrapped instanceof GameRecommenderException gameRecommenderException) {
            return gameRecommenderException.getErrorType().name();
        }
        return unwrapped != null ? unwrapped.getClass().getSimpleName() : "UNKNOWN";
    }

    public boolean isCircuitBreakerOpen(Throwable error) {
        return unwrap(error) instanceof CallNotPermittedException;
    }

    private boolean isFallbackUnavailableGrpcError(Throwable error) {
        Status.Code code = resolveStatusCode(error);
        return code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED
                || code == Status.Code.INTERNAL;
    }

    private Throwable unwrap(Throwable error) {
        return Exceptions.unwrap(error);
    }
}
