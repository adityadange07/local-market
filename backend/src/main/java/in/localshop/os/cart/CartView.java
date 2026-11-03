package in.localshop.os.cart;

import java.util.List;
import java.util.UUID;

public record CartView(UUID id, String shopSlug, String shopName, List<CartItemView> items,
        long subtotalMinor) { }
