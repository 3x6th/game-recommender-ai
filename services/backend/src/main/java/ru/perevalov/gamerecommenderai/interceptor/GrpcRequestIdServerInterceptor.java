package ru.perevalov.gamerecommenderai.interceptor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

@GrpcGlobalServerInterceptor
@Slf4j
public class GrpcRequestIdServerInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> REQUEST_ID_KEY =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    public static final Context.Key<String> REQUEST_ID_CONTEXT =
            Context.key("request_id");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {

        String requestId = headers.get(REQUEST_ID_KEY);

        if (requestId == null) {
            requestId = "unknown";
        }

        log.debug("RequestId from metadata: {}", requestId);

        Context context = Context.current().withValue(REQUEST_ID_CONTEXT, requestId);

        return Contexts.interceptCall(context, call, headers, next);
    }
}