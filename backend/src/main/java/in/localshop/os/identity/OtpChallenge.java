package in.localshop.os.identity;

import java.time.Instant;
import java.util.UUID;

/** Internal persistence projection. The code hash must never be serialized into an API response. */
public record OtpChallenge(
        UUID id,
        String phoneE164,
        OtpPurpose purpose,
        byte[] codeHash,
        Instant expiresAt,
        int attemptCount,
        int maxAttempts,
        OtpChallengeStatus status) {

    public OtpChallenge {
        codeHash = codeHash.clone();
    }

    @Override
    public byte[] codeHash() {
        return codeHash.clone();
    }
}
