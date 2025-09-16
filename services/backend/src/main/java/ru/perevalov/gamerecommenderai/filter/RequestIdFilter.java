package ru.perevalov.gamerecommenderai.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Фильтр для генерации request id каждого входящего запроса и его сохранения в заголовок.
 * Используем MDC - Mapped Diagnostic Context. Кладем в него сгенерированный requestId,
 * зачем очищаем чтобы не возникло коллизий.
 */
@Slf4j
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    @Value("${requestid.header.key}")
    private String requestIdHeaderKey;

    @Value("${requestid.logging.param}")
    private String requestIdLoggingParam;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String rqUID = request.getHeader(requestIdHeaderKey);

        if (rqUID == null || rqUID.isEmpty()) {
            rqUID = UUID.randomUUID().toString();
        }

        MDC.put(requestIdLoggingParam, rqUID);
        response.setHeader(requestIdHeaderKey, rqUID);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(requestIdLoggingParam);
        }
    }
}