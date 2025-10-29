package ru.perevalov.gamerecommenderai.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.filter.JwtRequestFilter;
import ru.perevalov.gamerecommenderai.security.TokenService;
import ru.perevalov.gamerecommenderai.security.jwt.JwtClaimKey;
import ru.perevalov.gamerecommenderai.security.jwt.JwtUtil;
import ru.perevalov.gamerecommenderai.service.UserService;
import ru.perevalov.gamerecommenderai.utils.DataUtils;

@ExtendWith(MockitoExtension.class)
class JwtRequestFilterTest {

    @Mock
    private UserService userService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private TokenService tokenService;

    private JwtRequestFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtRequestFilter(userService, tokenService, jwtUtil);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Test JWT authentication filter with valid Authorization header")
    void givenValidJwt_whenUserAuthenticates_thenDoFilterInternalWithJwt() throws Exception {
        // given
        Long mockSteamId = DataUtils.getMockSteamId();
        String jwt = "mockJwtToken";

        BDDMockito.given(request.getHeader(HttpHeaders.AUTHORIZATION))
                .willReturn("Bearer " + jwt);

        DecodedJWT decodedJWT = BDDMockito.mock(DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim claim = BDDMockito.mock(com.auth0.jwt.interfaces.Claim.class);
        BDDMockito.given(claim.asLong()).willReturn(mockSteamId);
        BDDMockito.given(decodedJWT.getClaim(JwtClaimKey.STEAM_ID.getKey())).willReturn(claim);
        BDDMockito.given(jwtUtil.decodeToken(jwt)).willReturn(decodedJWT);

        User userPersisted = DataUtils.getUserPersisted(mockSteamId);

        BDDMockito.given(userService.findBySteamId(mockSteamId)).willReturn(Mono.just(userPersisted));

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        Assertions.assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        Assertions.assertEquals(mockSteamId.toString(),
                SecurityContextHolder.getContext().getAuthentication().getName());
        BDDMockito.verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Test JWT authentication filter with missing Authorization header")
    void givenNoJwt_whenUserTryToAuthenticate_thenDoFilterInternalWithoutJwt() throws Exception {
        // given
        BDDMockito.given(request.getHeader(HttpHeaders.AUTHORIZATION)).willReturn(null);

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        Assertions.assertNull(SecurityContextHolder.getContext().getAuthentication());
        BDDMockito.verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Test JWT authentication filter with guest token (no steamId)")
    void givenGuestJwt_whenUserAuthenticates_thenDoFilterInternalWithGuest() throws Exception {
        // given
        String jwt = "guestJwtToken";

        BDDMockito.given(request.getHeader(HttpHeaders.AUTHORIZATION))
                .willReturn("Bearer " + jwt);

        DecodedJWT decodedJWT = BDDMockito.mock(DecodedJWT.class);
        BDDMockito.given(decodedJWT.getClaim(JwtClaimKey.STEAM_ID.getKey())).willReturn(null);
        BDDMockito.given(decodedJWT.getSubject()).willReturn("guest-session-123");

        BDDMockito.given(jwtUtil.decodeToken(jwt)).willReturn(decodedJWT);

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        Assertions.assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        Assertions.assertEquals("GUEST",
                SecurityContextHolder.getContext().getAuthentication().getName());
        BDDMockito.verify(filterChain).doFilter(request, response);
    }
}
