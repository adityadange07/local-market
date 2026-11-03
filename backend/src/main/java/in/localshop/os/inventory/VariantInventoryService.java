package in.localshop.os.inventory;

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
public class VariantInventoryService {

    private final JdbcTemplate jdbc;
    private final TenantContextHolder contextHolder;
    private final TenantDatabaseContext databaseContext;
    private final TransactionTemplate transactions;
    private final PermissionAuthorization permissions;
    private final AuditEventService audit;
    private final Clock clock;

    public VariantInventoryService(JdbcTemplate jdbc, TenantContextHolder contextHolder,
            TenantDatabaseContext databaseContext, PlatformTransactionManager transactionManager,
            PermissionAuthorization permissions, AuditEventService audit, Clock clock) {
        this.jdbc = jdbc;
        this.contextHolder = contextHolder;
        this.databaseContext = databaseContext;
        this.transactions = new TransactionTemplate(transactionManager);
        this.permissions = permissions;
        this.audit = audit;
        this.clock = clock;
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.catalog.read')")
    public List<VariantView> variants(UUID tenantId, UUID productId) {
        UUID userId = requirePermission(tenantId, "tenant.catalog.read");
        return transactions.execute(status -> sellerContext(userId, tenantId, () -> {
            requireProduct(tenantId, productId);
            return jdbc.query("SELECT id, product_id, variant_code, name, sku, price_minor, is_active, updated_at "
                    + "FROM product_variants WHERE tenant_id = ? AND product_id = ? "
                    + "ORDER BY variant_code, id", this::variantRow, tenantId, productId);
        }));
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.catalog.manage')")
    public VariantView createVariant(UUID tenantId, UUID productId, VariantRequest request) {
        UUID userId = requirePermission(tenantId, "tenant.catalog.manage");
        Instant now = clock.instant();
        try {
            return transactions.execute(status -> sellerContext(userId, tenantId, () -> {
                List<String> productStates = jdbc.query("SELECT status FROM products "
                        + "WHERE tenant_id = ? AND id = ? FOR UPDATE", (rs, row) -> rs.getString(1), tenantId, productId);
                if (productStates.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
                }
                if ("ARCHIVED".equals(productStates.getFirst())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived products cannot have variants.");
                }
                UUID variantId = UUID.randomUUID();
                jdbc.update("INSERT INTO product_variants (id, tenant_id, product_id, variant_code, name, sku, "
                                + "price_minor, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        variantId, tenantId, productId, request.variantCode().trim(), request.name().trim(),
                        blankToNull(request.sku()), request.priceMinor(), Timestamp.from(now), Timestamp.from(now));
                jdbc.update("INSERT INTO inventory (tenant_id, product_variant_id, quantity_on_hand, updated_at) "
                                + "VALUES (?, ?, 0, ?)", tenantId, variantId, Timestamp.from(now));
                audit.recordCurrent("inventory.variant.created", "product_variant", variantId.toString(),
                        Map.of("productId", productId.toString(), "variantCode", request.variantCode().trim()));
                return requireVariant(tenantId, variantId);
            }));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Variant code or SKU is already in use.");
        }
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.inventory.read')")
    public InventoryView inventory(UUID tenantId, UUID variantId) {
        UUID userId = requirePermission(tenantId, "tenant.inventory.read");
        return transactions.execute(status -> sellerContext(userId, tenantId,
                () -> requireInventory(tenantId, variantId)));
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.inventory.manage')")
    public InventoryView adjust(UUID tenantId, UUID variantId, StockAdjustmentRequest request) {
        UUID userId = requirePermission(tenantId, "tenant.inventory.manage");
        if (request.quantityDelta() == 0) {
            throw new IllegalArgumentException("Stock adjustment must be non-zero.");
        }
        Instant now = clock.instant();
        return transactions.execute(status -> sellerContext(userId, tenantId, () -> {
            List<Integer> quantities = jdbc.query("SELECT quantity_on_hand FROM inventory "
                            + "WHERE tenant_id = ? AND product_variant_id = ? FOR UPDATE",
                    (rs, row) -> rs.getInt(1), tenantId, variantId);
            if (quantities.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant inventory not found.");
            }
            int before = quantities.getFirst();
            long afterLong = (long) before + request.quantityDelta();
            if (afterLong < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock adjustment would make inventory negative.");
            }
            if (afterLong > Integer.MAX_VALUE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock quantity exceeds the supported limit.");
            }
            int after = (int) afterLong;
            jdbc.update("UPDATE inventory SET quantity_on_hand = ?, updated_at = ? "
                    + "WHERE tenant_id = ? AND product_variant_id = ?", after, Timestamp.from(now), tenantId, variantId);
            UUID movementId = UUID.randomUUID();
            jdbc.update("INSERT INTO stock_movements (id, tenant_id, product_variant_id, movement_type, "
                            + "quantity_delta, quantity_before, quantity_after, reason, created_by, created_at) "
                            + "VALUES (?, ?, ?, 'ADJUSTMENT', ?, ?, ?, ?, ?, ?)", movementId, tenantId, variantId,
                    request.quantityDelta(), before, after, request.reason().trim(), userId, Timestamp.from(now));
            audit.recordCurrent("inventory.stock.adjusted", "stock_movement", movementId.toString(),
                    Map.of("variantId", variantId.toString(), "delta", request.quantityDelta(),
                            "before", before, "after", after, "reason", request.reason().trim()));
            return new InventoryView(variantId, after, now);
        }));
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.inventory.read')")
    public List<StockMovementView> movements(UUID tenantId, UUID variantId, int limit) {
        UUID userId = requirePermission(tenantId, "tenant.inventory.read");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Movement page size must be between 1 and 100.");
        }
        return transactions.execute(status -> sellerContext(userId, tenantId, () -> {
            requireInventory(tenantId, variantId);
            return jdbc.query("SELECT id, product_variant_id, movement_type, quantity_delta, quantity_before, "
                            + "quantity_after, reason, created_at FROM stock_movements "
                            + "WHERE tenant_id = ? AND product_variant_id = ? "
                            + "ORDER BY created_at DESC, id DESC LIMIT ?",
                    (rs, row) -> new StockMovementView(rs.getObject("id", UUID.class),
                            rs.getObject("product_variant_id", UUID.class), rs.getString("movement_type"),
                            rs.getInt("quantity_delta"), rs.getInt("quantity_before"), rs.getInt("quantity_after"),
                            rs.getString("reason"), rs.getTimestamp("created_at").toInstant()),
                    tenantId, variantId, limit);
        }));
    }

    private InventoryView requireInventory(UUID tenantId, UUID variantId) {
        List<InventoryView> values = jdbc.query("SELECT product_variant_id, quantity_on_hand, updated_at "
                        + "FROM inventory WHERE tenant_id = ? AND product_variant_id = ?",
                (rs, row) -> new InventoryView(rs.getObject("product_variant_id", UUID.class),
                        rs.getInt("quantity_on_hand"), rs.getTimestamp("updated_at").toInstant()), tenantId, variantId);
        if (values.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant inventory not found.");
        }
        return values.getFirst();
    }

    private VariantView requireVariant(UUID tenantId, UUID variantId) {
        List<VariantView> values = jdbc.query("SELECT id, product_id, variant_code, name, sku, price_minor, "
                        + "is_active, updated_at FROM product_variants WHERE tenant_id = ? AND id = ?",
                this::variantRow, tenantId, variantId);
        if (values.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product variant not found.");
        return values.getFirst();
    }

    private VariantView variantRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Object price = rs.getObject("price_minor");
        return new VariantView(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getString("variant_code"), rs.getString("name"), rs.getString("sku"),
                price == null ? null : ((Number) price).longValue(), rs.getBoolean("is_active"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private void requireProduct(UUID tenantId, UUID productId) {
        if (jdbc.query("SELECT id FROM products WHERE tenant_id = ? AND id = ?", (rs, row) -> rs.getObject(1),
                tenantId, productId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
    }

    private UUID requirePermission(UUID tenantId, String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!permissions.hasTenant(auth, tenantId, permission)) {
            throw new AccessDeniedException("Active tenant permission required.");
        }
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Authenticated seller required.");
        }
        try { return UUID.fromString(jwt.getSubject()); }
        catch (IllegalArgumentException exception) { throw new AccessDeniedException("Authenticated seller required."); }
    }

    private <T> T sellerContext(UUID userId, UUID tenantId, Supplier<T> operation) {
        TenantExecutionContext context = new TenantExecutionContext(userId, tenantId, TenantActorScope.SELLER);
        try (TenantContextHolder.Scope ignored = contextHolder.install(context)) {
            databaseContext.applyCurrent();
            return operation.get();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
