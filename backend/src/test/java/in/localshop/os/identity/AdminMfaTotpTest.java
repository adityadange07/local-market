package in.localshop.os.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdminMfaTotpTest {

    @Test
    void base32SecretRoundTripsAndOnlyVerifiesWithinTheAllowedTimeWindow() {
        byte[] secret = new byte[20];
        for (int index = 0; index < secret.length; index++) {
            secret[index] = (byte) (index + 1);
        }
        String encoded = AdminMfaTotp.encodeSecret(secret);
        assertThat(AdminMfaTotp.decodeSecret(encoded)).containsExactly(secret);

        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        String code = AdminMfaTotp.code(secret, now);
        long step = now.getEpochSecond() / 30;
        assertThat(AdminMfaTotp.matchingStep(secret, code, now, null)).hasValue(step);
        assertThat(AdminMfaTotp.matchingStep(secret, code, now, step)).isEmpty();
        assertThat(AdminMfaTotp.matchingStep(secret, "invalid", now, null)).isEmpty();
    }

    @Test
    void previousOrNextTimeStepCanBeAcceptedForClockDrift() {
        byte[] secret = "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        Instant now = Instant.parse("2026-11-01T12:00:30Z");
        Instant previousStep = now.minusSeconds(30);
        String previousCode = AdminMfaTotp.code(secret, previousStep);

        assertThat(AdminMfaTotp.matchingStep(secret, previousCode, now, null))
                .hasValue(previousStep.getEpochSecond() / 30);
    }
}
