package ru.perevalov.gamerecommenderai.controller.resolver;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import ru.perevalov.gamerecommenderai.dto.OpenIdResponse;
import ru.perevalov.gamerecommenderai.security.openid.OpenIdParam;

/**
 * Класс для маппинга query-параметров запроса из url в DTO.
 * Используется в методе handleReturn() класса ru.perevalov.gamerecommenderai.controller.AuthController.java
 */
@Configuration
public class OpenIdResponseMethodArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return OpenIdResponse.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        return OpenIdResponse.builder()
                .ns(webRequest.getParameter(OpenIdParam.NS.getKey()))
                .mode(webRequest.getParameter(OpenIdParam.MODE.getKey()))
                .opEndpoint(webRequest.getParameter(OpenIdParam.OP_ENDPOINT.getKey()))
                .claimedId(webRequest.getParameter(OpenIdParam.CLAIMED_ID.getKey()))
                .identity(webRequest.getParameter(OpenIdParam.IDENTITY.getKey()))
                .returnTo(webRequest.getParameter(OpenIdParam.RETURN_TO.getKey()))
                .responseNonce(webRequest.getParameter(OpenIdParam.RESPONSE_NONCE.getKey()))
                .assocHandle(webRequest.getParameter(OpenIdParam.ASSOC_HANDLE.getKey()))
                .signed(webRequest.getParameter(OpenIdParam.SIGNED.getKey()))
                .sig(webRequest.getParameter(OpenIdParam.SIG.getKey()))
                .build();
    }
}