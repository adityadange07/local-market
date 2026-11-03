package in.localshop.os.files;

import java.time.Instant;
import java.util.UUID;

public record ProductImageView(UUID id, UUID productId, String contentType, int byteSize,
        String sha256, String scanStatus, int sortOrder, Instant createdAt) {
}
