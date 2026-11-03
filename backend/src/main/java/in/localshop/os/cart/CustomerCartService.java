package in.localshop.os.cart;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

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
public class CustomerCartService {
    private final JdbcTemplate jdbc;
    private final TenantContextHolder contextHolder;
    private final TenantDatabaseContext databaseContext;
    private final TransactionTemplate transactions;
    private final PermissionAuthorization permissions;
    private final Clock clock;

    public CustomerCartService(JdbcTemplate jdbc, TenantContextHolder contextHolder,
            TenantDatabaseContext databaseContext, PlatformTransactionManager transactionManager,
            PermissionAuthorization permissions, Clock clock) {
        this.jdbc = jdbc;
        this.contextHolder = contextHolder;
        this.databaseContext = databaseContext;
        this.transactions = new TransactionTemplate(transactionManager);
        this.permissions = permissions;
        this.clock = clock;
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'customer.cart.read')")
    public CartView view() {
        UUID userId = requireUser("customer.cart.read");
        return transactions.execute(status -> customerContext(userId, () -> loadCart(userId)));
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'customer.cart.write')")
    public CartView add(AddCartItemRequest request) {
        UUID userId = requireUser("customer.cart.write");
        validateQuantity(request.quantity());
        Instant now = clock.instant();
        try {
            return transactions.execute(status -> customerContext(userId, () -> {
                lockCustomer(userId);
                List<StoreRef> stores = jdbc.query("SELECT s.id, s.tenant_id FROM stores s "
                                + "JOIN tenants t ON t.id = s.tenant_id WHERE s.slug = ? "
                                + "AND s.status = 'PUBLISHED' AND t.status = 'APPROVED'",
                        (rs, row) -> new StoreRef(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class)),
                        request.shopSlug().trim());
                if (stores.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found.");
                StoreRef store = stores.getFirst();
                boolean validVariant = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS ("
                        + "SELECT 1 FROM product_variants v JOIN products p ON p.id = v.product_id "
                        + "AND p.tenant_id = v.tenant_id WHERE v.id = ? AND v.tenant_id = ? AND v.is_active "
                        + "AND p.store_id = ? AND p.status = 'PUBLISHED')", Boolean.class,
                        request.variantId(), store.tenantId(), store.storeId()));
                if (!validVariant) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product variant not found.");

                List<CartRef> active = jdbc.query("SELECT id, tenant_id, store_id FROM carts "
                                + "WHERE customer_id = ? AND status = 'ACTIVE' FOR UPDATE",
                        (rs, row) -> new CartRef(rs.getObject("id", UUID.class),
                                rs.getObject("tenant_id", UUID.class), rs.getObject("store_id", UUID.class)), userId);
                UUID cartId;
                if (active.isEmpty()) {
                    cartId = UUID.randomUUID();
                    jdbc.update("INSERT INTO carts (id, customer_id, tenant_id, store_id, status, created_at, updated_at) "
                                    + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)", cartId, userId, store.tenantId(),
                            store.storeId(), Timestamp.from(now), Timestamp.from(now));
                } else {
                    CartRef cart = active.getFirst();
                    if (!cart.tenantId().equals(store.tenantId()) || !cart.storeId().equals(store.storeId())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Your active cart belongs to another shop. Clear it before switching shops.");
                    }
                    cartId = cart.id();
                }
                List<Integer> quantities = jdbc.query("SELECT quantity FROM cart_items "
                                + "WHERE tenant_id = ? AND cart_id = ? AND product_variant_id = ? FOR UPDATE",
                        (rs, row) -> rs.getInt(1), store.tenantId(), cartId, request.variantId());
                int quantity = request.quantity() + (quantities.isEmpty() ? 0 : quantities.getFirst());
                if (quantity > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A cart item cannot exceed quantity 100.");
                if (quantities.isEmpty()) {
                    jdbc.update("INSERT INTO cart_items (id, tenant_id, cart_id, product_variant_id, quantity, "
                                    + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(),
                            store.tenantId(), cartId, request.variantId(), quantity, Timestamp.from(now), Timestamp.from(now));
                } else {
                    jdbc.update("UPDATE cart_items SET quantity = ?, updated_at = ? "
                                    + "WHERE tenant_id = ? AND cart_id = ? AND product_variant_id = ?",
                            quantity, Timestamp.from(now), store.tenantId(), cartId, request.variantId());
                }
                jdbc.update("UPDATE carts SET updated_at = ? WHERE id = ? AND customer_id = ?",
                        Timestamp.from(now), cartId, userId);
                return loadCart(userId);
            }));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart changed concurrently; retry the request.");
        }
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'customer.cart.write')")
    public CartView updateItem(UUID itemId, UpdateCartItemRequest request) {
        UUID userId = requireUser("customer.cart.write");
        validateQuantity(request.quantity());
        Instant now = clock.instant();
        return transactions.execute(status -> customerContext(userId, () -> {
            lockCustomer(userId);
            CartRef cart = lockActiveCart(userId);
            if (cart == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active cart not found.");
            List<UUID> items = jdbc.query("SELECT id FROM cart_items WHERE tenant_id = ? AND cart_id = ? AND id = ?",
                    (rs, row) -> rs.getObject(1, UUID.class), cart.tenantId(), cart.id(), itemId);
            if (items.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found.");
            jdbc.update("UPDATE cart_items SET quantity = ?, updated_at = ? WHERE tenant_id = ? AND cart_id = ? AND id = ?",
                    request.quantity(), Timestamp.from(now), cart.tenantId(), cart.id(), itemId);
            jdbc.update("UPDATE carts SET updated_at = ? WHERE id = ?", Timestamp.from(now), cart.id());
            return loadCart(userId);
        }));
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'customer.cart.write')")
    public CartView removeItem(UUID itemId) {
        UUID userId = requireUser("customer.cart.write");
        Instant now = clock.instant();
        return transactions.execute(status -> customerContext(userId, () -> {
            lockCustomer(userId);
            CartRef cart = lockActiveCart(userId);
            if (cart == null) return emptyCart();
            jdbc.update("DELETE FROM cart_items WHERE tenant_id = ? AND cart_id = ? AND id = ?",
                    cart.tenantId(), cart.id(), itemId);
            jdbc.update("UPDATE carts SET updated_at = ? WHERE id = ?", Timestamp.from(now), cart.id());
            return loadCart(userId);
        }));
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'customer.cart.write')")
    public CartView clear() {
        UUID userId = requireUser("customer.cart.write");
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> customerContext(userId, () -> {
            lockCustomer(userId);
            CartRef cart = lockActiveCart(userId);
            if (cart != null) jdbc.update("UPDATE carts SET status = 'ABANDONED', updated_at = ? WHERE id = ?",
                    Timestamp.from(now), cart.id());
            return null;
        }));
        return emptyCart();
    }

    private CartView loadCart(UUID userId) {
        List<CartRef> active = jdbc.query("SELECT c.id, c.tenant_id, c.store_id, s.slug, s.display_name "
                        + "FROM carts c JOIN stores s ON s.id = c.store_id AND s.tenant_id = c.tenant_id "
                        + "WHERE c.customer_id = ? AND c.status = 'ACTIVE'",
                (rs, row) -> new CartRef(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                        rs.getObject("store_id", UUID.class), rs.getString("slug"), rs.getString("display_name")), userId);
        if (active.isEmpty()) return emptyCart();
        CartRef cart = active.getFirst();
        List<CartItemView> items = jdbc.query("SELECT ci.id, v.id AS variant_id, p.slug AS product_slug, "
                        + "p.name AS product_name, v.variant_code, v.name AS variant_name, ci.quantity, "
                        + "COALESCE(v.price_minor, p.price_minor) AS unit_price FROM cart_items ci "
                        + "JOIN product_variants v ON v.id = ci.product_variant_id AND v.tenant_id = ci.tenant_id "
                        + "JOIN products p ON p.id = v.product_id AND p.tenant_id = v.tenant_id "
                        + "WHERE ci.tenant_id = ? AND ci.cart_id = ? ORDER BY ci.created_at, ci.id",
                (rs, row) -> {
                    long price = rs.getLong("unit_price");
                    int quantity = rs.getInt("quantity");
                    return new CartItemView(rs.getObject("id", UUID.class), rs.getObject("variant_id", UUID.class),
                            rs.getString("product_slug"), rs.getString("product_name"), rs.getString("variant_code"),
                            rs.getString("variant_name"), quantity, price, Math.multiplyExact(price, quantity));
                }, cart.tenantId(), cart.id());
        long subtotal = items.stream().mapToLong(CartItemView::lineTotalMinor).reduce(0L, Math::addExact);
        return new CartView(cart.id(), cart.slug(), cart.name(), items, subtotal);
    }

    private CartRef lockActiveCart(UUID userId) {
        List<CartRef> carts = jdbc.query("SELECT id, tenant_id, store_id FROM carts "
                        + "WHERE customer_id = ? AND status = 'ACTIVE' FOR UPDATE",
                (rs, row) -> new CartRef(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                        rs.getObject("store_id", UUID.class)), userId);
        return carts.isEmpty() ? null : carts.getFirst();
    }

    private void lockCustomer(UUID userId) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, userId.toString());
    }

    private UUID requireUser(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!permissions.hasGlobal(authentication, permission)) throw new AccessDeniedException("Customer permission required.");
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Authenticated customer required.");
        }
        try { return UUID.fromString(jwt.getSubject()); }
        catch (IllegalArgumentException exception) { throw new AccessDeniedException("Authenticated customer required."); }
    }

    private <T> T customerContext(UUID userId, Supplier<T> action) {
        TenantExecutionContext context = new TenantExecutionContext(userId, null, TenantActorScope.CUSTOMER);
        try (TenantContextHolder.Scope ignored = contextHolder.install(context)) {
            databaseContext.applyCurrent();
            return action.get();
        }
    }

    private static CartView emptyCart() { return new CartView(null, null, null, List.of(), 0); }
    private static void validateQuantity(int quantity) {
        if (quantity < 1 || quantity > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart item quantity must be between 1 and 100.");
        }
    }
    private record StoreRef(UUID storeId, UUID tenantId) { }
    private record CartRef(UUID id, UUID tenantId, UUID storeId, String slug, String name) {
        CartRef(UUID id, UUID tenantId, UUID storeId) { this(id, tenantId, storeId, null, null); }
    }
}
