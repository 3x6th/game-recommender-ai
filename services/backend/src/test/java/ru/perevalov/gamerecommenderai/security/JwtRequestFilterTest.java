package ru.perevalov.gamerecommenderai.security;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.security.jwt.JwtClaimKey;
import ru.perevalov.gamerecommenderai.security.jwt.JwtRequestFilter;
import ru.perevalov.gamerecommenderai.security.jwt.JwtUtil;
import ru.perevalov.gamerecommenderai.security.model.UserRole;
import ru.perevalov.gamerecommenderai.service.UserService;
import ru.perevalov.gamerecommenderai.utils.DataUtils;
import org.junit.jupiter.api.Assertions;

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

    private JwtRequestFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtRequestFilter(userService, jwtUtil);
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

        // мок DecodedJWT и Claim
        DecodedJWT decodedJWT = BDDMockito.mock(DecodedJWT.class);
        Claim claim = BDDMockito.mock(Claim.class);
        BDDMockito.given(claim.asLong()).willReturn(mockSteamId);
        BDDMockito.given(decodedJWT.getClaim(JwtClaimKey.STEAM_ID.getKey())).willReturn(claim);
        BDDMockito.given(jwtUtil.decodeToken(jwt)).willReturn(decodedJWT);

        // мок UserService
        User userPersisted = DataUtils.getUserPersisted(mockSteamId);
        BDDMockito.given(userService.findBySteamId(mockSteamId)).willReturn(userPersisted);

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        Assertions.assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        Assertions.assertEquals(String.valueOf(mockSteamId),
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

        // мок DecodedJWT и Claim без steamId
        DecodedJWT decodedJWT = BDDMockito.mock(DecodedJWT.class);
        Claim claim = BDDMockito.mock(Claim.class);
        BDDMockito.given(claim.asLong()).willReturn(null);
        BDDMockito.given(decodedJWT.getClaim(JwtClaimKey.STEAM_ID.getKey())).willReturn(claim);
        BDDMockito.given(decodedJWT.getSubject()).willReturn("guest-session-123");
        BDDMockito.given(jwtUtil.decodeToken(jwt)).willReturn(decodedJWT);

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        Assertions.assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        Assertions.assertTrue(SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals(UserRole.GUEST.getAuthority())));
    }
}