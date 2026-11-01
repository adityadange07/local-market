package in.localshop.os.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository {

    void create(OtpChallenge challenge);

    void markSent(UUID id, Instant sentAt);

    void markDeliveryFailed(UUID id);

    Optional<OtpChallenge> findById(UUID id);

    Optional<OtpChallengeStatus> recordFailedAttempt(UUID id, Instant attemptedAt);

    void markExpired(UUID id, Instant now);

    boolean consume(UUID id, Instant consumedAt);
}
