package in.localshop.os.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class AdminMfaTotp {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final long STEP_SECONDS = 30;

    private AdminMfaTotp() { }

    public static String encodeSecret(byte[] secret) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte item : secret) {
            buffer = (buffer << 8) | (item & 0xff);
            bits += 8;
            while (bits >= 5) {
                result.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            result.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return result.toString();
    }

    public static byte[] decodeSecret(String encoded) {
        String normalized = encoded.replace("=", "").toUpperCase(java.util.Locale.ROOT);
        byte[] output = new byte[normalized.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (char value : normalized.toCharArray()) {
            int digit = BASE32.indexOf(value);
            if (digit < 0) {
                throw new IllegalArgumentException("Invalid Base32 secret.");
            }
            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                output[index++] = (byte) (buffer >> (bits - 8));
                bits -= 8;
            }
        }
        return Arrays.copyOf(output, index);
    }

    public static OptionalLong matchingStep(byte[] secret, String code, Instant now, Long lastVerifiedStep) {
        long current = now.getEpochSecond() / STEP_SECONDS;
        for (long step = current - 1; step <= current + 1; step++) {
            if (step < 0 || (lastVerifiedStep != null && step <= lastVerifiedStep)) {
                continue;
            }
            if (constantTimeEquals(code.getBytes(StandardCharsets.US_ASCII), codeFor(secret, step))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    public static String code(byte[] secret, Instant instant) {
        return new String(codeFor(secret, instant.getEpochSecond() / STEP_SECONDS), StandardCharsets.US_ASCII);
    }

    private static byte[] codeFor(byte[] secret, long step) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA1");
            hmac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] digest = hmac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24)
                    | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8)
                    | (digest[offset + 3] & 0xff);
            return String.format(java.util.Locale.ROOT, "%06d", binary % 1_000_000)
                    .getBytes(StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP cryptography is unavailable.", exception);
        }
    }

    private static boolean constantTimeEquals(byte[] left, byte[] right) {
        return java.security.MessageDigest.isEqual(left, right);
    }
}
