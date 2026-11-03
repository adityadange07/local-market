package in.localshop.os.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ProductRequest(
        @NotNull UUID categoryId,
        @Size(max = 64) @Pattern(regexp = "^$|^[A-Z0-9][A-Z0-9._-]{0,63}$") String sku,
        @NotBlank @Size(min = 2, max = 100) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") String slug,
        @NotBlank @Size(min = 2, max = 180) String name,
        @Size(max = 4000) String description,
        @Positive long priceMinor,
        @Positive Long compareAtPriceMinor) { }
