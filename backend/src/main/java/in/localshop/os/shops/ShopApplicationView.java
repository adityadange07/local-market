package in.localshop.os.shops;

import java.time.Instant;
import java.util.UUID;

public record ShopApplicationView(
        UUID tenantId,
        String accountName,
        String storeName,
        String slug,
        String status,
        Instant createdAt,
        Instant reviewedAt,
        String rejectionReason) { }
