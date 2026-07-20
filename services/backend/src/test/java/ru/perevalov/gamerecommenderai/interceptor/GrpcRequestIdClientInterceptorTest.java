package ru.perevalov.gamerecommenderai.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

class GrpcRequestIdClientInterceptorTest {

    private static final Metadata.Key<String> CANONICAL_HEADER =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> HTTP_CORRELATION_HEADER =
            Metadata.Key.of("rquid", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> IDEMPOTENCY_HEADER =
            Metadata.Key.of("x-client-request-id", Metadata.ASCII_STRING_MARSHALLER);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void interceptCall_propagatesOnlyCanonicalCorrelationHeaderFromMdc() {
        Channel channel = mock(Channel.class);
        ClientCall clientCall = mock(ClientCall.class);
        MethodDescriptor method = mock(MethodDescriptor.class);
        ClientCall.Listener listener = mock(ClientCall.Listener.class);
        when(channel.newCall(any(), any())).thenReturn(clientCall);
        MDC.put("RequestID", " trace id\n");
        GrpcRequestIdClientInterceptor interceptor =
                new GrpcRequestIdClientInterceptor("RequestID");

        ClientCall intercepted = interceptor.interceptCall(
                method,
                CallOptions.DEFAULT,
                channel);
        intercepted.start(listener, new Metadata());

        ArgumentCaptor<Metadata> metadata = ArgumentCaptor.forClass(Metadata.class);
        verify(clientCall).start(any(), metadata.capture());
        assertThat(metadata.getValue().get(CANONICAL_HEADER)).isEqualTo("trace_id");
        assertThat(metadata.getValue().get(HTTP_CORRELATION_HEADER)).isNull();
        assertThat(metadata.getValue().get(IDEMPOTENCY_HEADER)).isNull();
    }
}
