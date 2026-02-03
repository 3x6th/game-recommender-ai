package ru.perevalov.gamerecommenderai.filter;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter for generating request id and attaching it to headers and MDC.
 */
@Slf4j
@Component
@Order(-90)
public class RequestIdWebFilter implements WebFilter {

    @Value("${requestid.header.key}")
    private String requestIdHeaderKey;

    @Value("${requestid.logging.param}")
    private String requestIdLoggingParam;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(requestIdHeaderKey);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(requestIdLoggingParam, requestId);
        exchange.getResponse().getHeaders().set(requestIdHeaderKey, requestId);

        return chain.filter(exchange)
                .doFinally(signalType -> MDC.remove(requestIdLoggingParam));
    }
}
