package in.localshop.os.identity;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Keyed OTP digest. A database-only disclosure does not permit offline OTP guessing. */
public final class OtpCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private final byte[] secret;

    public OtpCodeHasher(byte[] secret) {
        Objects.requireNonNull(secret, "secret");
        if (secret.length < 32) {
            throw new IllegalArgumentException("OTP HMAC secret must contain at least 32 bytes.");
        }
        this.secret = secret.clone();
    }

    public byte[] hash(UUID challengeId, String phoneE164, OtpPurpose purpose, String code) {
        String value = "otp-challenge\u0000" + challengeId + '\u0000' + purpose.name() + '\u0000'
                + phoneE164 + '\u0000' + code;
        return hmac(value);
    }

    /** Produces opaque rate-limit keys so raw phone numbers, IPs, and device IDs are not retained. */
    public byte[] hashRateLimitKey(String dimension, String subject) {
        return hmac("otp-rate-limit\u0000" + dimension + '\u0000' + subject);
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable.", exception);
        }
    }

    public boolean matches(byte[] expected, UUID challengeId, String phoneE164, OtpPurpose purpose, String code) {
        return MessageDigest.isEqual(expected, hash(challengeId, phoneE164, purpose, code));
    }
}
