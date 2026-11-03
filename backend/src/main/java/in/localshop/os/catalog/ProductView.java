package in.localshop.os.catalog;

import java.time.Instant;
import java.util.UUID;

public record ProductView(
        UUID id,
        UUID categoryId,
        String categoryName,
        String sku,
        String slug,
        String name,
        String description,
        long priceMinor,
        Long compareAtPriceMinor,
        String currency,
        String status,
        Instant updatedAt) { }
