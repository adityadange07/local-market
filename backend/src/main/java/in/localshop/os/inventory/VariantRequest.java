package in.localshop.os.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record VariantRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9._-]{0,63}$") String variantCode,
        @NotBlank @Size(max = 120) String name,
        @Pattern(regexp = "^$|^[A-Z0-9][A-Z0-9._-]{0,63}$") String sku,
        @Positive Long priceMinor) {
}
