package ru.perevalov.gamerecommenderai.security.steam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.OpenIdResponse;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.security.openid.OpenIdMode;
import ru.perevalov.gamerecommenderai.security.openid.OpenIdParam;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamOpenIdService {
    private final WebClient webClient;
    @Value("${steam.openid.endpoint}")
    private String steamOpenIdLoginUrl;

    /**
     * Отправляем запрос в Steam для проверки аутентификации пользователя.
     * Проверяем действительно ли запрос пришел от Steam, а не подделан.
     */
//    public void verifyResponse(OpenIdResponse openIdResponse) {
//        MultiValueMap<String, String> form = responseToMultiValueMap(openIdResponse);
//        String endpoint = openIdResponse.getOpEndpoint();
//        boolean opEndpointCheckOutSuccessfully = isValidOpEndpoint(endpoint);
//        if (!opEndpointCheckOutSuccessfully) {
//            throw new GameRecommenderException(ErrorType.OPENID_VALIDATION_FAILED_ENDPOINT, openIdResponse.getOpEndpoint());
//        }
//
//        String body = webClient.post()
//                .uri(endpoint)
//                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
//                .body(BodyInserters.fromFormData(form))
//                .retrieve()
//                .bodyToMono(String.class)
//                .block();
//
//        if (body == null || !body.contains("is_valid:true")) {
//            throw new GameRecommenderException(ErrorType.OPENID_VALIDATION_FAILED_RESPONSE, openIdResponse.getOpEndpoint(), body);
//        }
//    }
    public Mono<Void> verifyResponse(OpenIdResponse openIdResponse) {
        return Mono.fromCallable(() -> {
                    String endpoint = openIdResponse.getOpEndpoint();
                    boolean opEndpointCheckOutSuccessfully = isValidOpEndpoint(endpoint);
                    if (!opEndpointCheckOutSuccessfully) {
                        throw new GameRecommenderException(ErrorType.OPENID_VALIDATION_FAILED_ENDPOINT, openIdResponse.getOpEndpoint());
                    }
                    return endpoint;
                })
                .flatMap(validEndpoint -> {
                    MultiValueMap<String, String> form = responseToMultiValueMap(openIdResponse);

                    return webClient.post()
                            .uri(validEndpoint)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(BodyInserters.fromFormData(form))
                            .retrieve()
                            .bodyToMono(String.class)
                            .flatMap(body -> {
                                if (body == null || !body.contains("is_valid:true")) {
                                    return Mono.error(
                                            new GameRecommenderException(ErrorType.OPENID_VALIDATION_FAILED_ENDPOINT, openIdResponse.getOpEndpoint(), body)
                                    );
                                }
                                return Mono.empty();
                            });
                })
                .doOnSuccess(unused ->
                        log.debug("OpenID response verified successfully")
                )
                .doOnError(error ->
                        log.error("OpenID verification failed: {}", error.getMessage())
                )
                .then();
    }

    private boolean isValidOpEndpoint(String opEndpoint) {
        return opEndpoint.equals(steamOpenIdLoginUrl);
    }

    /**
     * Extracts the Steam ID from a claimedId URL.
     *
     * @param claimedId the claimedId obtained from an {@link OpenIdResponse} object
     * @return the Steam ID in long format
     * @throws GameRecommenderException if the claimedId format is invalid and Steam ID cannot be extracted
     * @see ErrorType#STEAM_ID_EXTRACTION_FAILED
     */
//    public Long extractSteamIdFromClaimedId(String claimedId) {
//        Matcher m = Pattern.compile(".*/id/(\\d+)$").matcher(claimedId);
//
//        if (!m.find()) {
//            log.error("Steam id extraction failed from claimedId={}", claimedId);
//            throw new GameRecommenderException(ErrorType.STEAM_ID_EXTRACTION_FAILED, claimedId);
//        }
//
//        String steamId64 = m.group(1);
//        return Long.parseLong(steamId64);
//    }
    public Mono<Long> extractSteamIdFromClaimedId(String claimedId) {
        return Mono.fromCallable(() -> {
                    Matcher m = Pattern.compile(".*/id/(\\d+)$").matcher(claimedId);

                    if (!m.find()) {
                        log.error("Steam id extraction failed from claimedId={}", claimedId);
                        throw new GameRecommenderException(ErrorType.STEAM_ID_EXTRACTION_FAILED, claimedId);
                    }

                    String steamId64 = m.group(1);
                    return Long.parseLong(steamId64);
                })
                .doOnSuccess(steamId ->
                        log.debug("Steam ID extracted: {}", steamId)
                )
                .doOnError(error ->
                        log.error("Steam ID extraction failed from claimedId={}", claimedId)
                );
    }

    private MultiValueMap<String, String> responseToMultiValueMap(OpenIdResponse openIdResponse) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add(OpenIdParam.NS.getKey(), openIdResponse.getNs());
        form.add(OpenIdParam.MODE.getKey(), OpenIdMode.CHECK_AUTHENTICATION.getValue());
        form.add(OpenIdParam.OP_ENDPOINT.getKey(), openIdResponse.getOpEndpoint());
        form.add(OpenIdParam.CLAIMED_ID.getKey(), openIdResponse.getClaimedId());
        form.add(OpenIdParam.IDENTITY.getKey(), openIdResponse.getIdentity());
        form.add(OpenIdParam.RETURN_TO.getKey(), openIdResponse.getReturnTo());
        form.add(OpenIdParam.RESPONSE_NONCE.getKey(), openIdResponse.getResponseNonce());
        form.add(OpenIdParam.ASSOC_HANDLE.getKey(), openIdResponse.getAssocHandle());
        form.add(OpenIdParam.SIGNED.getKey(), openIdResponse.getSigned());
        form.add(OpenIdParam.SIG.getKey(), openIdResponse.getSig());

        return form;
    }
}