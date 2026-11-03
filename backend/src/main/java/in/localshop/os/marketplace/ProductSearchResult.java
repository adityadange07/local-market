package in.localshop.os.marketplace;

import java.util.UUID;

public record ProductSearchResult(UUID productId, String productSlug, String productName,
        String categorySlug, String categoryName, long priceMinor, String shopSlug,
        String shopName, String city) { }
