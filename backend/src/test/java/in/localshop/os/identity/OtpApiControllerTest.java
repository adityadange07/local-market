package in.localshop.os.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import in.localshop.os.common.web.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class OtpApiControllerTest {

    private OtpChallengeService challengeService;
    private OtpRateLimitService rateLimitService;
    private OtpPhoneNormalizer phoneNormalizer;
    private IdentityAccountService identityAccountService;
    private AuthTokenService authTokenService;
    private OtpApiController controller;

    @BeforeEach
    void setUp() {
        challengeService = mock(OtpChallengeService.class);
        rateLimitService = mock(OtpRateLimitService.class);
        phoneNormalizer = mock(OtpPhoneNormalizer.class);
        identityAccountService = mock(IdentityAccountService.class);
        authTokenService = mock(AuthTokenService.class);
        controller = new OtpApiController(
                challengeService,
                rateLimitService,
                phoneNormalizer,
                new OtpClientAddressResolver(java.util.Set.of()),
                identityAccountService,
                authTokenService,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC")));
    }

    @Test
    void requestReturnsAcceptedAndAccountNeutralBody() {
        UUID challengeId = UUID.randomUUID();
        when(phoneNormalizer.normalize("98765 43210")).thenReturn("+919876543210");
        when(challengeService.request("+919876543210", OtpPurpose.CUSTOMER_LOGIN)).thenReturn(challengeId);
        MockHttpServletRequest servletRequest = request();

        var response = controller.request(
                new OtpRequestDto("98765 43210", OtpPurpose.CUSTOMER_LOGIN, "device_0123456789"), servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().challengeId()).isEqualTo(challengeId);
        assertThat(response.getBody().message()).doesNotContain("registered", "+919876543210");
    }

    @Test
    void validOtpResolvesIdentityAndReturnsNextActionWithoutUserOrTokenDetails() {
        UUID challengeId = UUID.randomUUID();
        when(phoneNormalizer.normalize("9876543210")).thenReturn("+919876543210");
        when(challengeService.verify(challengeId, "+919876543210", OtpPurpose.CUSTOMER_LOGIN, "012345"))
                .thenReturn(OtpVerificationResult.VERIFIED);
        when(identityAccountService.resolveAfterVerifiedOtp("+919876543210", OtpPurpose.CUSTOMER_LOGIN))
                .thenReturn(new IdentityResolution(UUID.randomUUID(), "SESSION_ISSUANCE"));
        when(authTokenService.issue(any(), eq("device_0123456789")))
                .thenReturn(new TokenResponse("access", "refresh", "Bearer", 600,
                        Instant.parse("2026-01-31T00:00:00Z")));

        var response = controller.verify(new OtpVerifyDto(
                challengeId, "9876543210", OtpPurpose.CUSTOMER_LOGIN, "012345", "device_0123456789"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().verified()).isTrue();
        assertThat(response.getBody().nextAction()).isEqualTo("SESSION_ISSUANCE");
        assertThat(response.getBody().tokens().accessToken()).isEqualTo("access");
        assertThat(response.getBody().toString()).doesNotContain("userId");
    }

    @Test
    void invalidAndUnknownChallengesUseOneGenericUnauthorizedResponse() {
        UUID challengeId = UUID.randomUUID();
        when(phoneNormalizer.normalize("9876543210")).thenReturn("+919876543210");
        when(challengeService.verify(eq(challengeId), eq("+919876543210"), eq(OtpPurpose.CUSTOMER_LOGIN), any()))
                .thenReturn(OtpVerificationResult.NOT_FOUND, OtpVerificationResult.LOCKED);
        OtpVerifyDto dto = new OtpVerifyDto(
                challengeId, "9876543210", OtpPurpose.CUSTOMER_LOGIN, "012345", "device_0123456789");

        IdentityAuthenticationRejectedException unknown = assertThrows(IdentityAuthenticationRejectedException.class,
                () -> controller.verify(dto, request()));
        IdentityAuthenticationRejectedException locked = assertThrows(IdentityAuthenticationRejectedException.class,
                () -> controller.verify(dto, request()));

        assertThat(unknown.getMessage()).isEqualTo(locked.getMessage());
    }

    @Test
    void rateLimitResponseIncludesRetryHeaderAndCommonErrorEnvelope() {
        MockHttpServletRequest request = request();
        request.setAttribute(TraceIdFilter.REQUEST_ATTRIBUTE, "trace-123");

        var response = controller.handleRateLimit(new OtpRateLimitException(42), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("42");
        assertThat(response.getBody().code()).isEqualTo("RATE_LIMITED");
        assertThat(response.getBody().traceId()).isEqualTo("trace-123");
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        return request;
    }
}
