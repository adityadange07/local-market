package in.localshop.os.inventory;

import java.time.Instant;
import java.util.UUID;

public record InventoryView(UUID variantId, int quantityOnHand, Instant updatedAt) {
}
