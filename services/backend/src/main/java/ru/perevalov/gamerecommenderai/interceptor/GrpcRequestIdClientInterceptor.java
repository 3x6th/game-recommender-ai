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
import org.springframework.stereotype.Component;

/**
 * Перехватчик вызовов от Grpc клиентов. При каждом вызове будет добавляться request id в заголовок из MDC контекста
 */
@Component
public class GrpcRequestIdClientInterceptor implements ClientInterceptor {
    @Value("${requestid.header.key}")
    private String requestHeaderKey;

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions, Channel next) {
        Metadata headers = new Metadata();
        String rqUid = MDC.get(requestHeaderKey);

        if (rqUid != null && !rqUid.isEmpty()) {
            Metadata.Key<String> rqUidHeader = Metadata.Key.of(requestHeaderKey, Metadata.ASCII_STRING_MARSHALLER);
            headers.put(rqUidHeader, rqUid);
        }

        return MetadataUtils.newAttachHeadersInterceptor(headers).interceptCall(method, callOptions, next);
    }
}