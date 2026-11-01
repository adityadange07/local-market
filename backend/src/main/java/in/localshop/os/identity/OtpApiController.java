package in.localshop.os.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import in.localshop.os.common.error.ApiError;
import in.localshop.os.common.error.ApiErrorCode;
import in.localshop.os.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(SmsProvider.class)
@RequestMapping("/api/v1/auth/otp")
public class OtpApiController {

    private static final String REQUEST_MESSAGE = "If this number can use Local Shop OS, an OTP will be sent.";
    private final OtpChallengeService challengeService;
    private final OtpRateLimitService rateLimitService;
    private final OtpPhoneNormalizer phoneNormalizer;
    private final OtpClientAddressResolver clientAddressResolver;
    private final IdentityAccountService identityAccountService;
    private final AuthTokenService authTokenService;
    private final Clock clock;

    public OtpApiController(
            OtpChallengeService challengeService,
            OtpRateLimitService rateLimitService,
            OtpPhoneNormalizer phoneNormalizer,
            OtpClientAddressResolver clientAddressResolver,
            IdentityAccountService identityAccountService,
            AuthTokenService authTokenService,
            Clock clock) {
        this.challengeService = challengeService;
        this.rateLimitService = rateLimitService;
        this.phoneNormalizer = phoneNormalizer;
        this.clientAddressResolver = clientAddressResolver;
        this.identityAccountService = identityAccountService;
        this.authTokenService = authTokenService;
        this.clock = clock;
    }

    @PostMapping("/request")
    public ResponseEntity<OtpRequestResponse> request(
            @Valid @RequestBody OtpRequestDto requestDto,
            HttpServletRequest servletRequest) {
        String phone = phoneNormalizer.normalize(requestDto.phone());
        rateLimitService.checkAndConsume(
                OtpRateLimitService.Operation.REQUEST,
                phone,
                clientAddressResolver.resolve(servletRequest),
                requestDto.deviceId(),
                clock.instant());
        UUID challengeId = challengeService.request(phone, requestDto.purpose());
        return ResponseEntity.accepted().body(new OtpRequestResponse(challengeId, REQUEST_MESSAGE));
    }

    @PostMapping("/verify")
    public ResponseEntity<OtpVerifyResponse> verify(
            @Valid @RequestBody OtpVerifyDto requestDto,
            HttpServletRequest servletRequest) {
        String phone = phoneNormalizer.normalize(requestDto.phone());
        rateLimitService.checkAndConsume(
                OtpRateLimitService.Operation.VERIFY,
                phone,
                clientAddressResolver.resolve(servletRequest),
                requestDto.deviceId(),
                clock.instant());
        OtpVerificationResult result = challengeService.verify(
                requestDto.challengeId(), phone, requestDto.purpose(), requestDto.code());
        if (result != OtpVerificationResult.VERIFIED) {
            throw new IdentityAuthenticationRejectedException();
        }
        IdentityResolution identity = identityAccountService.resolveAfterVerifiedOtp(phone, requestDto.purpose());
        TokenResponse tokens = authTokenService.issue(identity.userId(), requestDto.deviceId());
        return ResponseEntity.ok(new OtpVerifyResponse(
                true, identity.nextAction(), tokens, "OTP verified."));
    }

    @ExceptionHandler(OtpRateLimitException.class)
    public ResponseEntity<ApiError> handleRateLimit(
            OtpRateLimitException exception, HttpServletRequest request) {
        Object trace = request.getAttribute(TraceIdFilter.REQUEST_ATTRIBUTE);
        String traceId = trace instanceof String value ? value : UUID.randomUUID().toString();
        ApiError error = new ApiError(
                ApiErrorCode.RATE_LIMITED.name(),
                "Too many authentication requests. Try again later.",
                Map.of(),
                traceId);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(exception.retryAfterSeconds()))
                .body(error);
    }
}
