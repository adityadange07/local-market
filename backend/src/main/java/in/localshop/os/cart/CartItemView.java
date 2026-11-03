package in.localshop.os.cart;

import java.util.UUID;

public record CartItemView(UUID id, UUID variantId, String productSlug, String productName,
        String variantCode, String variantName, int quantity, long unitPriceMinor, long lineTotalMinor) { }
