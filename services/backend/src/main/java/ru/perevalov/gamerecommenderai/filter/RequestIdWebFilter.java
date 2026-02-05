package ru.perevalov.gamerecommenderai.filter;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter for generating request id and attaching it to headers and Reactor context.
 */
@Slf4j
@Component
@Order(-90)
public class RequestIdWebFilter implements WebFilter {

    @Value("${requestid.header.key}")
    private String requestIdHeaderKey;

    @Value("${requestid.logging.param}")
    private String requestIdLoggingParam;

    public static final String REQUEST_ID_CONTEXT_KEY = "requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(requestIdHeaderKey);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        final String finalRequestId = requestId;

        exchange.getResponse().getHeaders().set(requestIdHeaderKey, finalRequestId);

        return chain.filter(exchange)
                .contextWrite(context -> context.put(REQUEST_ID_CONTEXT_KEY, finalRequestId)
                .put(requestIdLoggingParam, finalRequestId));
    }
}
