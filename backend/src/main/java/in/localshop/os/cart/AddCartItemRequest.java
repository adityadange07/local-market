package in.localshop.os.cart;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddCartItemRequest(@NotBlank @Size(max = 100) String shopSlug,
        @NotNull UUID variantId, @Min(1) @Max(100) int quantity) { }
