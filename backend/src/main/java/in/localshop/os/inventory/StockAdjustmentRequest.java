package in.localshop.os.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(@NotNull Integer quantityDelta,
        @NotBlank @Size(max = 240) String reason) {
}
