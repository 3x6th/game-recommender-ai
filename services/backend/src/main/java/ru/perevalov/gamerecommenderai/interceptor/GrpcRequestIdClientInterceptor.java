package ru.perevalov.gamerecommenderai.interceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.stub.MetadataUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import ru.perevalov.gamerecommenderai.util.RequestIdUtils;

/**
 * Перехватчик вызовов от Grpc клиентов. При каждом вызове будет добавляться request id в заголовок из MDC контекста
 */
@GrpcGlobalClientInterceptor
public class GrpcRequestIdClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> CANONICAL_REQUEST_ID_HEADER =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    private final String requestIdLoggingParam;

    public GrpcRequestIdClientInterceptor(
            @Value("${requestid.logging.param}") String requestIdLoggingParam) {
        this.requestIdLoggingParam = requestIdLoggingParam;
    }


    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions, Channel next) {
        Metadata headers = new Metadata();
        String requestId = RequestIdUtils.normalizeOrNull(MDC.get(requestIdLoggingParam));

        if (requestId != null) {
            headers.put(CANONICAL_REQUEST_ID_HEADER, requestId);
        }

        return MetadataUtils.newAttachHeadersInterceptor(headers).interceptCall(method, callOptions, next);
    }
}
