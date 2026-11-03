package in.localshop.os.customer;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import in.localshop.os.identity.PermissionAuthorization;
import in.localshop.os.tenancy.TenantActorScope;
import in.localshop.os.tenancy.TenantContextHolder;
import in.localshop.os.tenancy.TenantDatabaseContext;
import in.localshop.os.tenancy.TenantExecutionContext;
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
public class CustomerAddressService {
    private final JdbcTemplate jdbc;
    private final TenantContextHolder contextHolder;
    private final TenantDatabaseContext databaseContext;
    private final TransactionTemplate transactions;
    private final PermissionAuthorization permissions;
    private final Clock clock;

    public CustomerAddressService(JdbcTemplate jdbc, TenantContextHolder contextHolder,
            TenantDatabaseContext databaseContext, PlatformTransactionManager transactionManager,
            PermissionAuthorization permissions, Clock clock) {
        this.jdbc = jdbc;
        this.contextHolder = contextHolder;
        this.databaseContext = databaseContext;
        this.transactions = new TransactionTemplate(transactionManager);
        this.permissions = permissions;
        this.clock = clock;
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'customer.address.read')")
    public List<CustomerAddressView> list() {
        UUID userId = requireUser("customer.address.read");
        return transactions.execute(status -> inCustomerContext(userId, () -> jdbc.query(selectSql()
                        + " WHERE user_id = ? AND deleted_at IS NULL ORDER BY is_default DESC, updated_at DESC, id",
                this::mapAddress, userId)));
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'customer.address.write')")
    public CustomerAddressView create(CustomerAddressRequest request) {
        UUID userId = requireUser("customer.address.write");
        Instant now = clock.instant();
        return transactions.execute(status -> inCustomerContext(userId, () -> {
            lockCustomer(userId);
            boolean makeDefault = request.defaultAddress() || !hasActiveAddress(userId);
            if (makeDefault) clearDefault(userId);
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO customer_addresses (id, user_id, label, recipient_name, recipient_phone, "
                            + "address_line1, address_line2, landmark, locality, city, state, postal_code, location, "
                            + "is_default, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                            + "CASE WHEN CAST(? AS DOUBLE PRECISION) IS NULL OR CAST(? AS DOUBLE PRECISION) IS NULL THEN NULL ELSE "
                            + "ST_SetSRID(ST_MakePoint(?, ?),4326)::geography END, ?, ?, ?)",
                    id, userId, request.label().trim(), request.recipientName().trim(), blankToNull(request.recipientPhone()),
                    request.addressLine1().trim(), blankToNull(request.addressLine2()), blankToNull(request.landmark()),
                    request.locality().trim(), request.city().trim(), request.state().trim(), request.postalCode(),
                    request.longitude(), request.latitude(), request.longitude(), request.latitude(), makeDefault,
                    Timestamp.from(now), Timestamp.from(now));
            return requireAddress(userId, id);
        }));
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'customer.address.write')")
    public CustomerAddressView update(UUID addressId, CustomerAddressRequest request) {
        UUID userId = requireUser("customer.address.write");
        Instant now = clock.instant();
        return transactions.execute(status -> inCustomerContext(userId, () -> {
            lockCustomer(userId);
            requireAddress(userId, addressId);
            if (request.defaultAddress()) clearDefault(userId);
            jdbc.update("UPDATE customer_addresses SET label = ?, recipient_name = ?, recipient_phone = ?, "
                            + "address_line1 = ?, address_line2 = ?, landmark = ?, locality = ?, city = ?, state = ?, "
                            + "postal_code = ?, location = CASE WHEN CAST(? AS DOUBLE PRECISION) IS NULL "
                            + "OR CAST(? AS DOUBLE PRECISION) IS NULL THEN NULL ELSE "
                            + "ST_SetSRID(ST_MakePoint(?, ?),4326)::geography END, is_default = ?, updated_at = ? "
                            + "WHERE user_id = ? AND id = ? AND deleted_at IS NULL",
                    request.label().trim(), request.recipientName().trim(), blankToNull(request.recipientPhone()),
                    request.addressLine1().trim(), blankToNull(request.addressLine2()), blankToNull(request.landmark()),
                    request.locality().trim(), request.city().trim(), request.state().trim(), request.postalCode(),
                    request.longitude(), request.latitude(), request.longitude(), request.latitude(),
                    request.defaultAddress(), Timestamp.from(now), userId, addressId);
            return requireAddress(userId, addressId);
        }));
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'customer.address.write')")
    public void delete(UUID addressId) {
        UUID userId = requireUser("customer.address.write");
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> inCustomerContext(userId, () -> {
            lockCustomer(userId);
            requireAddress(userId, addressId);
            jdbc.update("UPDATE customer_addresses SET deleted_at = ?, is_default = FALSE, updated_at = ? "
                    + "WHERE user_id = ? AND id = ? AND deleted_at IS NULL",
                    Timestamp.from(now), Timestamp.from(now), userId, addressId);
            return null;
        }));
    }

    private boolean hasActiveAddress(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM customer_addresses "
                + "WHERE user_id = ? AND deleted_at IS NULL)", Boolean.class, userId));
    }

    private void lockCustomer(UUID userId) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, userId.toString());
    }

    private void clearDefault(UUID userId) {
        jdbc.update("UPDATE customer_addresses SET is_default = FALSE WHERE user_id = ? "
                + "AND deleted_at IS NULL AND is_default", userId);
    }

    private CustomerAddressView requireAddress(UUID userId, UUID id) {
        List<CustomerAddressView> rows = jdbc.query(selectSql()
                        + " WHERE user_id = ? AND id = ? AND deleted_at IS NULL",
                this::mapAddress, userId, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer address not found.");
        return rows.getFirst();
    }

    private String selectSql() {
        return "SELECT id, label, recipient_name, recipient_phone, address_line1, address_line2, landmark, locality, "
                + "city, state, postal_code, ST_Y(location::geometry) AS latitude, "
                + "ST_X(location::geometry) AS longitude, is_default, updated_at FROM customer_addresses";
    }

    private CustomerAddressView mapAddress(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Object latitude = rs.getObject("latitude");
        Object longitude = rs.getObject("longitude");
        return new CustomerAddressView(rs.getObject("id", UUID.class), rs.getString("label"),
                rs.getString("recipient_name"), rs.getString("recipient_phone"), rs.getString("address_line1"),
                rs.getString("address_line2"), rs.getString("landmark"), rs.getString("locality"),
                rs.getString("city"), rs.getString("state"), rs.getString("postal_code"),
                latitude == null ? null : ((Number) latitude).doubleValue(),
                longitude == null ? null : ((Number) longitude).doubleValue(), rs.getBoolean("is_default"),
                rs.getTimestamp("updated_at").toInstant());
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

    private <T> T inCustomerContext(UUID userId, java.util.function.Supplier<T> action) {
        TenantExecutionContext context = new TenantExecutionContext(userId, null, TenantActorScope.CUSTOMER);
        try (TenantContextHolder.Scope ignored = contextHolder.install(context)) {
            databaseContext.applyCurrent();
            return action.get();
        }
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
