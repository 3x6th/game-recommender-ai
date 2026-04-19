package ru.perevalov.gamerecommenderai.interceptor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

/**
 * Достаёт {@code x-request-id} из gRPC metadata и кладёт в {@link Context} текущего
 * gRPC-вызова.
 * <p>
 * Сервисные методы (реактивные, {@code ReactorJavaToolsServiceGrpc.JavaToolsServiceImplBase})
 * читают значение синхронно в момент сборки Mono через {@link #REQUEST_ID_CONTEXT}
 * и прокидывают его в Reactor Context единственным {@code .contextWrite(...)} в конце
 * цепочки. Это безопасно, потому что сборка {@code Mono} выполняется на той же gRPC-нити,
 * на которой {@link Context} ещё активен. Дальше MDC синхронизируется через
 * {@code ReactorMdcConfiguration} (см. PR #45).
 */
@GrpcGlobalServerInterceptor
@Slf4j
public class GrpcRequestIdServerInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> REQUEST_ID_KEY =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    public static final String REQUEST_ID_FALLBACK = "unknown";

    public static final Context.Key<String> REQUEST_ID_CONTEXT =
            Context.key("request_id");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {

        String requestId = headers.get(REQUEST_ID_KEY);
        if (requestId == null || requestId.isBlank()) {
            requestId = REQUEST_ID_FALLBACK;
        }

        log.debug("RequestId from metadata: {}", requestId);

        Context context = Context.current().withValue(REQUEST_ID_CONTEXT, requestId);
        return Contexts.interceptCall(context, call, headers, next);
    }
}
