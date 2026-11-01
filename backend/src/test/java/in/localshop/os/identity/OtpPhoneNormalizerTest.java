package in.localshop.os.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OtpPhoneNormalizerTest {

    private final OtpPhoneNormalizer normalizer = new OtpPhoneNormalizer();

    @Test
    void normalizesIndianLocalAndInternationalFormats() {
        assertThat(normalizer.normalize("98765 43210")).isEqualTo("+919876543210");
        assertThat(normalizer.normalize("098765-43210")).isEqualTo("+919876543210");
        assertThat(normalizer.normalize("+1 (202) 555-0100")).isEqualTo("+12025550100");
        assertThat(normalizer.normalize("0091 98765 43210")).isEqualTo("+919876543210");
    }

    @Test
    void rejectsMalformedOrAmbiguousPhoneNumbers() {
        assertThrows(InvalidOtpPhoneException.class, () -> normalizer.normalize("98765ABC210"));
        assertThrows(InvalidOtpPhoneException.class, () -> normalizer.normalize("12345"));
        assertThrows(InvalidOtpPhoneException.class, () -> normalizer.normalize("+0123456789"));
    }
}
