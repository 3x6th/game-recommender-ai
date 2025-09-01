package ru.perevalov.gamerecommenderai.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OpenIdResponse {
    private String ns;
    private String mode;
    private String opEndpoint;
    private String claimedId;
    private String identity;
    private String returnTo;
    private String responseNonce;
    private String assocHandle;
    private String signed;
    private String sig;
}
