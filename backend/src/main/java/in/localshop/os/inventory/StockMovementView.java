package in.localshop.os.inventory;

import java.time.Instant;
import java.util.UUID;

public record StockMovementView(UUID id, UUID variantId, String type, int quantityDelta,
        int quantityBefore, int quantityAfter, String reason, Instant createdAt) {
}
