package in.localshop.os.catalog;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import in.localshop.os.audit.AuditEventService;
import in.localshop.os.identity.PermissionAuthorization;
import in.localshop.os.tenancy.TenantActorScope;
import in.localshop.os.tenancy.TenantContextHolder;
import in.localshop.os.tenancy.TenantDatabaseContext;
import in.localshop.os.tenancy.TenantExecutionContext;
import org.springframework.dao.DuplicateKeyException;
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
import org.springframework.http.HttpStatus;

@Service
public class ProductCatalogService {

    private static final String PRODUCT_COLUMNS = "p.id, p.category_id, c.name AS category_name, p.sku, p.slug, "
            + "p.name, p.description, p.price_minor, p.compare_at_price_minor, p.currency, p.status, p.updated_at";

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextHolder contextHolder;
    private final TenantDatabaseContext databaseContext;
    private final TransactionTemplate transactionTemplate;
    private final PermissionAuthorization permissionAuthorization;
    private final AuditEventService auditEventService;
    private final Clock clock;

    public ProductCatalogService(
            JdbcTemplate jdbcTemplate,
            TenantContextHolder contextHolder,
            TenantDatabaseContext databaseContext,
            PlatformTransactionManager transactionManager,
            PermissionAuthorization permissionAuthorization,
            AuditEventService auditEventService,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.contextHolder = contextHolder;
        this.databaseContext = databaseContext;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.permissionAuthorization = permissionAuthorization;
        this.auditEventService = auditEventService;
        this.clock = clock;
    }

    public List<CategoryView> activeCategories() {
        return jdbcTemplate.query(
                "SELECT id, parent_id, name, slug, description, sort_order FROM categories "
                        + "WHERE is_active ORDER BY sort_order, name, id",
                (rs, row) -> new CategoryView(rs.getObject("id", UUID.class),
                        rs.getObject("parent_id", UUID.class), rs.getString("name"), rs.getString("slug"),
                        rs.getString("description"), rs.getInt("sort_order")));
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.catalog.read')")
    public ProductList sellerProducts(UUID tenantId, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Product page size must be between 1 and 100.");
        }
        UUID userId = requirePermission(tenantId, "tenant.catalog.read");
        return transactionTemplate.execute(status -> inSellerContext(userId, tenantId, () -> {
            List<ProductView> products = jdbcTemplate.query(
                    "SELECT " + PRODUCT_COLUMNS + " FROM products p JOIN categories c ON c.id = p.category_id "
                            + "WHERE p.tenant_id = ? ORDER BY p.updated_at DESC, p.id DESC LIMIT ?",
                    this::productRow, tenantId, limit);
            return new ProductList(products);
        }));
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.catalog.manage')")
    public ProductView create(UUID tenantId, ProductRequest request) {
        UUID userId = requirePermission(tenantId, "tenant.catalog.manage");
        validatePrices(request);
        Instant now = clock.instant();
        try {
            return transactionTemplate.execute(status -> inSellerContext(userId, tenantId, () -> {
                UUID storeId = requireStoreId(tenantId);
                requireActiveCategory(request.categoryId());
                UUID productId = UUID.randomUUID();
                jdbcTemplate.update(
                        "INSERT INTO products (id, tenant_id, store_id, category_id, sku, slug, name, description, "
                                + "price_minor, compare_at_price_minor, currency, status, created_by, created_at, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'INR', 'DRAFT', ?, ?, ?)",
                        productId, tenantId, storeId, request.categoryId(), blankToNull(request.sku()),
                        request.slug().trim(), request.name().trim(), blankToNull(request.description()),
                        request.priceMinor(), request.compareAtPriceMinor(), userId, Timestamp.from(now), Timestamp.from(now));
                auditEventService.recordCurrent("catalog.product.created", "product", productId.toString(),
                        java.util.Map.of("categoryId", request.categoryId().toString(), "status", "DRAFT"));
                return requireProduct(tenantId, productId);
            }));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product SKU or URL is already in use.");
        }
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.catalog.manage')")
    public ProductView update(UUID tenantId, UUID productId, ProductRequest request) {
        UUID userId = requirePermission(tenantId, "tenant.catalog.manage");
        validatePrices(request);
        Instant now = clock.instant();
        try {
            return transactionTemplate.execute(status -> inSellerContext(userId, tenantId, () -> {
                ProductView current = requireProduct(tenantId, productId);
                if ("ARCHIVED".equals(current.status())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived products cannot be edited.");
                }
                requireActiveCategory(request.categoryId());
                jdbcTemplate.update(
                        "UPDATE products SET category_id = ?, sku = ?, slug = ?, name = ?, description = ?, "
                                + "price_minor = ?, compare_at_price_minor = ?, updated_by = ?, updated_at = ? "
                                + "WHERE id = ? AND tenant_id = ?",
                        request.categoryId(), blankToNull(request.sku()), request.slug().trim(), request.name().trim(),
                        blankToNull(request.description()), request.priceMinor(), request.compareAtPriceMinor(),
                        userId, Timestamp.from(now), productId, tenantId);
                auditEventService.recordCurrent("catalog.product.updated", "product", productId.toString(),
                        java.util.Map.of("fields", List.of("categoryId", "sku", "slug", "name", "description", "price")));
                return requireProduct(tenantId, productId);
            }));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product SKU or URL is already in use.");
        }
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.catalog.manage')")
    public ProductView publish(UUID tenantId, UUID productId) {
        UUID userId = requirePermission(tenantId, "tenant.catalog.manage");
        Instant now = clock.instant();
        return transactionTemplate.execute(status -> inSellerContext(userId, tenantId, () -> {
            List<String> storeStates = jdbcTemplate.query(
                    "SELECT t.status || ':' || s.status FROM tenants t JOIN stores s ON s.tenant_id = t.id "
                            + "WHERE t.id = ? FOR UPDATE OF t, s",
                    (rs, row) -> rs.getString(1), tenantId);
            if (storeStates.isEmpty() || !"APPROVED:PUBLISHED".equals(storeStates.getFirst())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The approved shop must be published before its products.");
            }
            ProductView current = requireProduct(tenantId, productId);
            if ("ARCHIVED".equals(current.status())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived products cannot be published.");
            }
            requireActiveCategory(current.categoryId());
            jdbcTemplate.update(
                    "UPDATE products SET status = 'PUBLISHED', archived_at = NULL, updated_by = ?, updated_at = ? "
                            + "WHERE id = ? AND tenant_id = ?",
                    userId, Timestamp.from(now), productId, tenantId);
            auditEventService.recordCurrent("catalog.product.published", "product", productId.toString(),
                    java.util.Map.of("status", "PUBLISHED"));
            return requireProduct(tenantId, productId);
        }));
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.catalog.manage')")
    public ProductView archive(UUID tenantId, UUID productId) {
        UUID userId = requirePermission(tenantId, "tenant.catalog.manage");
        Instant now = clock.instant();
        return transactionTemplate.execute(status -> inSellerContext(userId, tenantId, () -> {
            ProductView current = requireProduct(tenantId, productId);
            if ("ARCHIVED".equals(current.status())) {
                return current;
            }
            jdbcTemplate.update(
                    "UPDATE products SET status = 'ARCHIVED', archived_at = ?, updated_by = ?, updated_at = ? "
                            + "WHERE id = ? AND tenant_id = ?",
                    Timestamp.from(now), userId, Timestamp.from(now), productId, tenantId);
            auditEventService.recordCurrent("catalog.product.archived", "product", productId.toString(),
                    java.util.Map.of("status", "ARCHIVED"));
            return requireProduct(tenantId, productId);
        }));
    }

    public ProductList publicProducts(String shopSlug, String categorySlug, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Product page size must be between 1 and 100.");
        }
        return transactionTemplate.execute(status -> inPublicContext(() -> {
            List<ProductView> products = jdbcTemplate.query(
                    "SELECT " + PRODUCT_COLUMNS + " FROM products p JOIN categories c ON c.id = p.category_id "
                            + "JOIN stores s ON s.id = p.store_id JOIN tenants t ON t.id = s.tenant_id "
                            + "WHERE s.slug = ? AND s.status = 'PUBLISHED' AND t.status = 'APPROVED' "
                            + "AND p.status = 'PUBLISHED' AND c.is_active AND (?::TEXT IS NULL OR c.slug = ?) "
                            + "ORDER BY p.name, p.id LIMIT ?",
                    this::productRow, shopSlug, categorySlug, categorySlug, limit);
            return new ProductList(products);
        }));
    }

    private ProductView requireProduct(UUID tenantId, UUID productId) {
        List<ProductView> rows = jdbcTemplate.query(
                "SELECT " + PRODUCT_COLUMNS + " FROM products p JOIN categories c ON c.id = p.category_id "
                        + "WHERE p.tenant_id = ? AND p.id = ?",
                this::productRow, tenantId, productId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
        return rows.getFirst();
    }

    private UUID requireStoreId(UUID tenantId) {
        List<UUID> stores = jdbcTemplate.query(
                "SELECT id FROM stores WHERE tenant_id = ?", (rs, row) -> rs.getObject(1, UUID.class), tenantId);
        if (stores.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found.");
        }
        return stores.getFirst();
    }

    private void requireActiveCategory(UUID categoryId) {
        List<Boolean> states = jdbcTemplate.query(
                "SELECT is_active FROM categories WHERE id = ?",
                (rs, row) -> rs.getBoolean(1), categoryId);
        if (states.isEmpty() || !states.getFirst()) {
            throw new IllegalArgumentException("An active product category is required.");
        }
    }

    private UUID requirePermission(UUID tenantId, String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!permissionAuthorization.hasTenant(authentication, tenantId, permission)) {
            throw new AccessDeniedException("Active tenant permission required.");
        }
        return currentUserId(authentication);
    }

    private <T> T inSellerContext(UUID userId, UUID tenantId, java.util.function.Supplier<T> operation) {
        return inContext(userId, tenantId, TenantActorScope.SELLER, operation);
    }

    private <T> T inPublicContext(java.util.function.Supplier<T> operation) {
        return inContext(null, null, TenantActorScope.PUBLIC, operation);
    }

    private <T> T inContext(UUID userId, UUID tenantId, TenantActorScope scope,
            java.util.function.Supplier<T> operation) {
        TenantExecutionContext context = new TenantExecutionContext(userId, tenantId, scope);
        try (TenantContextHolder.Scope ignored = contextHolder.install(context)) {
            databaseContext.applyCurrent();
            return operation.get();
        }
    }

    private ProductView productRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Object compareAt = rs.getObject("compare_at_price_minor");
        return new ProductView(rs.getObject("id", UUID.class), rs.getObject("category_id", UUID.class),
                rs.getString("category_name"), rs.getString("sku"), rs.getString("slug"), rs.getString("name"),
                rs.getString("description"), rs.getLong("price_minor"),
                compareAt == null ? null : ((Number) compareAt).longValue(), rs.getString("currency"),
                rs.getString("status"), rs.getTimestamp("updated_at").toInstant());
    }

    private static void validatePrices(ProductRequest request) {
        if (request.compareAtPriceMinor() != null && request.compareAtPriceMinor() < request.priceMinor()) {
            throw new IllegalArgumentException("Compare-at price must be at least the selling price.");
        }
    }

    private static UUID currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Authenticated seller required.");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Authenticated seller required.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
