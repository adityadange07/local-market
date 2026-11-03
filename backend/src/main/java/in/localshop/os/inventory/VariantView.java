package in.localshop.os.inventory;

import java.time.Instant;
import java.util.UUID;

public record VariantView(UUID id, UUID productId, String variantCode, String name,
        String sku, Long priceMinor, boolean active, Instant updatedAt) {
}
