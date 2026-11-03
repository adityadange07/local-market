package in.localshop.os.files;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import in.localshop.os.audit.AuditEventService;
import in.localshop.os.identity.PermissionAuthorization;
import in.localshop.os.tenancy.TenantActorScope;
import in.localshop.os.tenancy.TenantContextHolder;
import in.localshop.os.tenancy.TenantDatabaseContext;
import in.localshop.os.tenancy.TenantExecutionContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductImageService {
    private final JdbcTemplate jdbc;
    private final TenantContextHolder contextHolder;
    private final TenantDatabaseContext databaseContext;
    private final TransactionTemplate transactions;
    private final PermissionAuthorization permissions;
    private final AuditEventService audit;
    private final FileStorage storage;
    private final MalwareScanner scanner;
    private final Clock clock;

    public ProductImageService(JdbcTemplate jdbc, TenantContextHolder contextHolder,
            TenantDatabaseContext databaseContext, PlatformTransactionManager transactionManager,
            PermissionAuthorization permissions, AuditEventService audit, FileStorage storage,
            MalwareScanner scanner, Clock clock) {
        this.jdbc = jdbc;
        this.contextHolder = contextHolder;
        this.databaseContext = databaseContext;
        this.transactions = new TransactionTemplate(transactionManager);
        this.permissions = permissions;
        this.audit = audit;
        this.storage = storage;
        this.scanner = scanner;
        this.clock = clock;
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.catalog.manage')")
    public ProductImageView upload(UUID tenantId, UUID productId, byte[] bytes) {
        UUID userId = requirePermission(tenantId, "tenant.catalog.manage");
        String contentType = ImageUploadPolicy.verifyAndDetect(bytes);
        MalwareScanner.ScanStatus scanStatus = scanner.scan(bytes, contentType);
        if (scanStatus == null || scanStatus == MalwareScanner.ScanStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Image did not pass the safety scan.");
        }
        UUID imageId = UUID.randomUUID();
        String objectKey = tenantId + "/" + imageId;
        Instant now = clock.instant();
        try {
            storage.put(objectKey, bytes);
            return transactions.execute(status -> sellerContext(userId, tenantId, () -> {
                List<String> states = jdbc.query("SELECT status FROM products WHERE tenant_id = ? AND id = ? FOR UPDATE",
                        (rs, row) -> rs.getString(1), tenantId, productId);
                if (states.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
                if ("ARCHIVED".equals(states.getFirst())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived products cannot have images.");
                }
                Integer nextOrder = jdbc.queryForObject("SELECT COALESCE(MAX(sort_order) + 1, 0) FROM product_images "
                        + "WHERE tenant_id = ? AND product_id = ?", Integer.class, tenantId, productId);
                jdbc.update("INSERT INTO product_images (id, tenant_id, product_id, object_key, content_type, "
                                + "byte_size, sha256_hex, scan_status, sort_order, created_by, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", imageId, tenantId, productId,
                        objectKey, contentType, bytes.length, ImageUploadPolicy.sha256(bytes), scanStatus.name(),
                        nextOrder, userId, Timestamp.from(now));
                audit.recordCurrent("catalog.product_image.uploaded", "product_image", imageId.toString(),
                        Map.of("productId", productId.toString(), "contentType", contentType,
                                "byteSize", bytes.length, "scanStatus", scanStatus.name()));
                return requireImage(tenantId, imageId);
            }));
        } catch (RuntimeException exception) {
            try { storage.delete(objectKey); } catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            if (exception instanceof DuplicateKeyException) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Image could not be attached.");
            }
            throw exception;
        }
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.catalog.read')")
    public List<ProductImageView> sellerImages(UUID tenantId, UUID productId) {
        UUID userId = requirePermission(tenantId, "tenant.catalog.read");
        return transactions.execute(status -> sellerContext(userId, tenantId, () -> {
            requireProduct(tenantId, productId);
            return jdbc.query("SELECT id, product_id, content_type, byte_size, sha256_hex, scan_status, "
                            + "sort_order, created_at FROM product_images WHERE tenant_id = ? AND product_id = ? "
                            + "ORDER BY sort_order, id", this::imageRow, tenantId, productId);
        }));
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.catalog.manage')")
    public void delete(UUID tenantId, UUID productId, UUID imageId) {
        UUID userId = requirePermission(tenantId, "tenant.catalog.manage");
        String objectKey = transactions.execute(status -> sellerContext(userId, tenantId, () -> {
            List<String> keys = jdbc.query("SELECT object_key FROM product_images "
                    + "WHERE tenant_id = ? AND product_id = ? AND id = ? FOR UPDATE",
                    (rs, row) -> rs.getString(1), tenantId, productId, imageId);
            if (keys.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product image not found.");
            jdbc.update("DELETE FROM product_images WHERE tenant_id = ? AND id = ?", tenantId, imageId);
            audit.recordCurrent("catalog.product_image.deleted", "product_image", imageId.toString(), Map.of());
            return keys.getFirst();
        }));
        storage.delete(objectKey);
    }

    public ProductImageDownload publicImage(String shopSlug, String productSlug, UUID imageId) {
        return transactions.execute(status -> publicContext(() -> {
            List<ImageMetadata> rows = jdbc.query("SELECT i.object_key, i.content_type FROM product_images i "
                            + "JOIN products p ON p.id = i.product_id AND p.tenant_id = i.tenant_id "
                            + "JOIN stores s ON s.id = p.store_id AND s.tenant_id = p.tenant_id "
                            + "JOIN tenants t ON t.id = s.tenant_id "
                            + "WHERE s.slug = ? AND p.slug = ? AND i.id = ? AND i.scan_status = 'CLEAN' "
                            + "AND p.status = 'PUBLISHED' AND s.status = 'PUBLISHED' AND t.status = 'APPROVED'",
                    (rs, row) -> new ImageMetadata(rs.getString("object_key"), rs.getString("content_type")),
                    shopSlug, productSlug, imageId);
            if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product image not found.");
            ImageMetadata metadata = rows.getFirst();
            return new ProductImageDownload(metadata.contentType(), storage.read(metadata.objectKey()));
        }));
    }

    private ProductImageView requireImage(UUID tenantId, UUID imageId) {
        List<ProductImageView> rows = jdbc.query("SELECT id, product_id, content_type, byte_size, sha256_hex, "
                        + "scan_status, sort_order, created_at FROM product_images WHERE tenant_id = ? AND id = ?",
                this::imageRow, tenantId, imageId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product image not found.");
        return rows.getFirst();
    }

    private ProductImageView imageRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new ProductImageView(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getString("content_type"), rs.getInt("byte_size"), rs.getString("sha256_hex"),
                rs.getString("scan_status"), rs.getInt("sort_order"), rs.getTimestamp("created_at").toInstant());
    }

    private void requireProduct(UUID tenantId, UUID productId) {
        if (jdbc.query("SELECT id FROM products WHERE tenant_id = ? AND id = ?", (rs, row) -> rs.getObject(1),
                tenantId, productId).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
    }

    private UUID requirePermission(UUID tenantId, String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!permissions.hasTenant(authentication, tenantId, permission)) throw new AccessDeniedException("Active tenant permission required.");
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Authenticated seller required.");
        }
        try { return UUID.fromString(jwt.getSubject()); }
        catch (IllegalArgumentException exception) { throw new AccessDeniedException("Authenticated seller required."); }
    }

    private <T> T sellerContext(UUID userId, UUID tenantId, Supplier<T> operation) {
        return context(new TenantExecutionContext(userId, tenantId, TenantActorScope.SELLER), operation);
    }

    private <T> T publicContext(Supplier<T> operation) {
        return context(new TenantExecutionContext(null, null, TenantActorScope.PUBLIC), operation);
    }

    private <T> T context(TenantExecutionContext executionContext, Supplier<T> operation) {
        try (TenantContextHolder.Scope ignored = contextHolder.install(executionContext)) {
            databaseContext.applyCurrent();
            return operation.get();
        }
    }

    private record ImageMetadata(String objectKey, String contentType) { }
}
