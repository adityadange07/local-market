package in.localshop.os.identity;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Coordinates short-lived challenges without retaining the delivered code. */
public final class OtpChallengeService {

    private static final int CODE_SPACE = 1_000_000;
    private final OtpChallengeRepository repository;
    private final SmsProvider smsProvider;
    private final OtpCodeHasher codeHasher;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final Duration lifetime;
    private final int maxAttempts;

    public OtpChallengeService(
            OtpChallengeRepository repository,
            SmsProvider smsProvider,
            OtpCodeHasher codeHasher,
            SecureRandom secureRandom,
            Clock clock,
            Duration lifetime,
            int maxAttempts) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.smsProvider = Objects.requireNonNull(smsProvider, "smsProvider");
        this.codeHasher = Objects.requireNonNull(codeHasher, "codeHasher");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        if (lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("OTP lifetime must be between 1 second and 10 minutes.");
        }
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("Maximum OTP attempts must be between 1 and 10.");
        }
        this.maxAttempts = maxAttempts;
    }

    /** Returns only an opaque challenge ID; callers must use a generic public response. */
    public UUID request(String phoneE164, OtpPurpose purpose) {
        Objects.requireNonNull(phoneE164, "phoneE164");
        Objects.requireNonNull(purpose, "purpose");
        Instant now = clock.instant();
        String code = "%06d".formatted(secureRandom.nextInt(CODE_SPACE));
        UUID id = UUID.randomUUID();
        OtpChallenge challenge = new OtpChallenge(
                id, phoneE164, purpose, codeHasher.hash(id, phoneE164, purpose, code),
                now.plus(lifetime), 0, maxAttempts, OtpChallengeStatus.PENDING);
        repository.create(challenge);

        try {
            smsProvider.sendOtp(phoneE164, purpose, code);
        } catch (RuntimeException deliveryFailure) {
            repository.markDeliveryFailed(id);
            return id;
        }
        repository.markSent(id, clock.instant());
        return id;
    }

    public OtpVerificationResult verify(UUID id, String phoneE164, OtpPurpose purpose, String code) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(phoneE164, "phoneE164");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(code, "code");
        Instant now = clock.instant();
        OtpChallenge challenge = repository.findById(id).orElse(null);
        if (challenge == null || !challenge.phoneE164().equals(phoneE164) || challenge.purpose() != purpose) {
            return OtpVerificationResult.NOT_FOUND;
        }
        if (challenge.status() == OtpChallengeStatus.DELIVERY_FAILED || challenge.status() == OtpChallengeStatus.PENDING) {
            return OtpVerificationResult.NOT_DELIVERED;
        }
        if (challenge.status() == OtpChallengeStatus.CONSUMED) {
            return OtpVerificationResult.ALREADY_USED;
        }
        if (challenge.status() == OtpChallengeStatus.LOCKED || challenge.attemptCount() >= challenge.maxAttempts()) {
            return OtpVerificationResult.LOCKED;
        }
        if (challenge.status() == OtpChallengeStatus.EXPIRED || !challenge.expiresAt().isAfter(now)) {
            repository.markExpired(id, now);
            return OtpVerificationResult.EXPIRED;
        }
        if (challenge.status() != OtpChallengeStatus.SENT) {
            return OtpVerificationResult.NOT_DELIVERED;
        }
        if (!codeHasher.matches(challenge.codeHash(), id, phoneE164, purpose, code)) {
            OtpChallengeStatus updatedStatus = repository.recordFailedAttempt(id, now).orElse(challenge.status());
            return updatedStatus == OtpChallengeStatus.LOCKED
                    ? OtpVerificationResult.LOCKED
                    : OtpVerificationResult.INVALID;
        }
        if (repository.consume(id, now)) {
            return OtpVerificationResult.VERIFIED;
        }
        OtpChallengeStatus currentStatus = repository.findById(id)
                .map(OtpChallenge::status)
                .orElse(OtpChallengeStatus.EXPIRED);
        if (currentStatus == OtpChallengeStatus.LOCKED) {
            return OtpVerificationResult.LOCKED;
        }
        if (currentStatus == OtpChallengeStatus.EXPIRED || !challenge.expiresAt().isAfter(clock.instant())) {
            repository.markExpired(id, clock.instant());
            return OtpVerificationResult.EXPIRED;
        }
        return OtpVerificationResult.ALREADY_USED;
    }
}
