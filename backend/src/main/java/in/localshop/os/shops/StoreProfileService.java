package in.localshop.os.shops;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import in.localshop.os.audit.AuditEventService;
import in.localshop.os.identity.PermissionAuthorization;
import in.localshop.os.marketplace.PublicStorefrontView;
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
public class StoreProfileService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextHolder contextHolder;
    private final TenantDatabaseContext databaseContext;
    private final TransactionTemplate transactionTemplate;
    private final PermissionAuthorization permissionAuthorization;
    private final AuditEventService auditEventService;
    private final Clock clock;

    public StoreProfileService(
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

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.store.read')")
    public SellerStoreView getSellerStore(UUID tenantId) {
        UUID userId = requirePermission(tenantId, "tenant.store.read");
        return transactionTemplate.execute(status -> inSellerContext(userId, tenantId,
                () -> loadSellerView(tenantId)));
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.store.manage')")
    public SellerStoreView updateProfile(UUID tenantId, StoreProfileRequest request) {
        UUID userId = requirePermission(tenantId, "tenant.store.manage");
        Instant now = clock.instant();
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        try {
            return transactionTemplate.execute(status -> inSellerContext(userId, tenantId, () -> {
                UUID storeId = requireStoreId(tenantId);
                jdbcTemplate.update(
                        "UPDATE stores SET display_name = ?, description = ?, slug = ?, updated_by = ?, updated_at = ? "
                                + "WHERE tenant_id = ?",
                        request.displayName().trim(), blankToNull(request.description()), slug, userId,
                        Timestamp.from(now), tenantId);
                auditEventService.recordCurrent("shop.store_profile.updated", "store", storeId.toString(),
                        java.util.Map.of("fields", List.of("displayName", "description", "slug")));
                return loadSellerView(tenantId);
            }));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Shop URL is unavailable.");
        }
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.store.manage')")
    public SellerStoreView updateAddress(UUID tenantId, StoreAddressRequest request) {
        UUID userId = requirePermission(tenantId, "tenant.store.manage");
        Instant now = clock.instant();
        return transactionTemplate.execute(status -> inSellerContext(userId, tenantId, () -> {
            UUID storeId = requireStoreId(tenantId);
            jdbcTemplate.update(
                    "INSERT INTO store_addresses (id, tenant_id, store_id, address_line1, address_line2, landmark, "
                            + "locality, city, state, postal_code, location, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?) "
                            + "ON CONFLICT (tenant_id, store_id) DO UPDATE SET address_line1 = EXCLUDED.address_line1, "
                            + "address_line2 = EXCLUDED.address_line2, landmark = EXCLUDED.landmark, locality = EXCLUDED.locality, "
                            + "city = EXCLUDED.city, state = EXCLUDED.state, postal_code = EXCLUDED.postal_code, "
                            + "location = EXCLUDED.location, updated_at = EXCLUDED.updated_at",
                    UUID.randomUUID(), tenantId, storeId, request.addressLine1().trim(), blankToNull(request.addressLine2()),
                    blankToNull(request.landmark()), request.locality().trim(), request.city().trim(), request.state().trim(),
                    request.postalCode(), request.longitude(), request.latitude(), Timestamp.from(now), Timestamp.from(now));
            auditEventService.recordCurrent("shop.store_address.updated", "store", storeId.toString(),
                    java.util.Map.of("addressUpdated", true));
            return loadSellerView(tenantId);
        }));
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.store.manage')")
    public SellerStoreView updateFulfillment(UUID tenantId, StoreFulfillmentRequest request) {
        UUID userId = requirePermission(tenantId, "tenant.store.manage");
        validateFulfillment(request);
        Instant now = clock.instant();
        return transactionTemplate.execute(status -> inSellerContext(userId, tenantId, () -> {
            UUID storeId = requireStoreId(tenantId);
            jdbcTemplate.update(
                    "INSERT INTO store_fulfillment_settings (tenant_id, store_id, delivery_enabled, pickup_enabled, "
                            + "delivery_radius_meters, minimum_order_minor, timezone, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON CONFLICT (tenant_id, store_id) DO UPDATE SET delivery_enabled = EXCLUDED.delivery_enabled, "
                            + "pickup_enabled = EXCLUDED.pickup_enabled, delivery_radius_meters = EXCLUDED.delivery_radius_meters, "
                            + "minimum_order_minor = EXCLUDED.minimum_order_minor, timezone = EXCLUDED.timezone, "
                            + "updated_at = EXCLUDED.updated_at",
                    tenantId, storeId, request.deliveryEnabled(), request.pickupEnabled(),
                    request.deliveryRadiusMeters(), request.minimumOrderMinor(), request.timezone(),
                    Timestamp.from(now), Timestamp.from(now));
            auditEventService.recordCurrent("shop.fulfillment.updated", "store", storeId.toString(),
                    java.util.Map.of("deliveryEnabled", request.deliveryEnabled(),
                            "pickupEnabled", request.pickupEnabled()));
            return loadSellerView(tenantId);
        }));
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.store.manage')")
    public SellerStoreView updateHours(UUID tenantId, StoreHoursRequest request) {
        UUID userId = requirePermission(tenantId, "tenant.store.manage");
        validateHours(request);
        Instant now = clock.instant();
        return transactionTemplate.execute(status -> inSellerContext(userId, tenantId, () -> {
            UUID storeId = requireStoreId(tenantId);
            for (StoreHourRequest hour : request.hours()) {
                jdbcTemplate.update(
                        "INSERT INTO store_hours (id, tenant_id, store_id, day_of_week, opens_at, closes_at, is_closed, "
                                + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                                + "ON CONFLICT (tenant_id, store_id, day_of_week) DO UPDATE SET "
                                + "opens_at = EXCLUDED.opens_at, closes_at = EXCLUDED.closes_at, "
                                + "is_closed = EXCLUDED.is_closed, updated_at = EXCLUDED.updated_at",
                        UUID.randomUUID(), tenantId, storeId, hour.dayOfWeek(), hour.opensAt(), hour.closesAt(),
                        hour.closed(), Timestamp.from(now), Timestamp.from(now));
            }
            auditEventService.recordCurrent("shop.hours.updated", "store", storeId.toString(),
                    java.util.Map.of("daysConfigured", request.hours().size()));
            return loadSellerView(tenantId);
        }));
    }

    @PreAuthorize("@permissionAuthorization.hasTenant(authentication, #p0, 'tenant.store.manage')")
    public SellerStoreView publish(UUID tenantId) {
        UUID userId = requirePermission(tenantId, "tenant.store.manage");
        Instant now = clock.instant();
        return transactionTemplate.execute(status -> inSellerContext(userId, tenantId, () -> {
            List<String> tenantStates = jdbcTemplate.query(
                    "SELECT status FROM tenants WHERE id = ?", (rs, row) -> rs.getString(1), tenantId);
            if (tenantStates.isEmpty() || !"APPROVED".equals(tenantStates.getFirst())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Shop must be approved before publishing.");
            }
            UUID storeId = requireStoreId(tenantId);
            List<String> statuses = jdbcTemplate.query(
                    "SELECT status FROM stores WHERE id = ? AND tenant_id = ? FOR UPDATE",
                    (rs, row) -> rs.getString(1), storeId, tenantId);
            String storeStatus = statuses.getFirst();
            if ("PUBLISHED".equals(storeStatus)) {
                return loadSellerView(tenantId);
            }
            if (!"DRAFT".equals(storeStatus)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived shops cannot be published.");
            }
            Long ready = jdbcTemplate.queryForObject(
                    "SELECT CASE WHEN EXISTS (SELECT 1 FROM store_addresses WHERE tenant_id = ? AND store_id = ?) "
                            + "AND EXISTS (SELECT 1 FROM store_fulfillment_settings WHERE tenant_id = ? AND store_id = ? "
                            + "AND (delivery_enabled OR pickup_enabled)) "
                            + "AND (SELECT count(*) FROM store_hours WHERE tenant_id = ? AND store_id = ?) = 7 "
                            + "AND EXISTS (SELECT 1 FROM store_hours WHERE tenant_id = ? AND store_id = ? AND NOT is_closed) "
                            + "THEN 1 ELSE 0 END",
                    Long.class, tenantId, storeId, tenantId, storeId, tenantId, storeId, tenantId, storeId);
            if (ready == null || ready != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Complete the address, weekly hours, and fulfillment settings before publishing.");
            }
            jdbcTemplate.update("UPDATE stores SET status = 'PUBLISHED', updated_by = ?, updated_at = ? WHERE id = ?",
                    userId, Timestamp.from(now), storeId);
            auditEventService.recordCurrent("shop.store_published", "store", storeId.toString(),
                    java.util.Map.of("status", "PUBLISHED"));
            return loadSellerView(tenantId);
        }));
    }

    public PublicStorefrontView publicStorefront(String slug) {
        return transactionTemplate.execute(status -> inPublicContext(() -> {
            List<Object[]> stores = jdbcTemplate.query(
                    "SELECT s.id, s.display_name, s.description, s.slug FROM stores s JOIN tenants t ON t.id = s.tenant_id "
                            + "WHERE s.slug = ? AND s.status = 'PUBLISHED' AND t.status = 'APPROVED'",
                    (rs, row) -> new Object[]{rs.getObject("id", UUID.class), rs.getString("display_name"),
                            rs.getString("description"), rs.getString("slug")}, slug);
            if (stores.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found.");
            }
            Object[] store = stores.getFirst();
            UUID storeId = (UUID) store[0];
            StoreAddressView address = loadAddress(storeId, null);
            StoreFulfillmentView fulfillment = loadFulfillment(storeId, null);
            List<StoreHourView> hours = loadHours(storeId, null);
            return new PublicStorefrontView((String) store[1], (String) store[2], (String) store[3],
                    address, fulfillment, hours);
        }));
    }

    private SellerStoreView loadSellerView(UUID tenantId) {
        UUID storeId = requireStoreId(tenantId);
        List<SellerStoreView> rows = jdbcTemplate.query(
                "SELECT id, display_name, description, slug, status FROM stores WHERE tenant_id = ?",
                (rs, row) -> new SellerStoreView(rs.getObject("id", UUID.class), rs.getString("display_name"),
                        rs.getString("description"), rs.getString("slug"), rs.getString("status"),
                        null, null, List.of()), tenantId);
        SellerStoreView base = rows.getFirst();
        return new SellerStoreView(base.storeId(), base.displayName(), base.description(), base.slug(), base.status(),
                loadAddress(storeId, tenantId), loadFulfillment(storeId, tenantId), loadHours(storeId, tenantId));
    }

    private StoreAddressView loadAddress(UUID storeId, UUID tenantId) {
        String sql = "SELECT address_line1, address_line2, landmark, locality, city, state, postal_code, "
                + "ST_Y(location::geometry) AS latitude, ST_X(location::geometry) AS longitude "
                + "FROM store_addresses WHERE store_id = ?" + (tenantId == null ? "" : " AND tenant_id = ?");
        List<StoreAddressView> rows = tenantId == null
                ? jdbcTemplate.query(sql, (rs, row) -> addressRow(rs), storeId)
                : jdbcTemplate.query(sql, (rs, row) -> addressRow(rs), storeId, tenantId);
        return rows.stream().findFirst().orElse(null);
    }

    private StoreFulfillmentView loadFulfillment(UUID storeId, UUID tenantId) {
        String sql = "SELECT delivery_enabled, pickup_enabled, delivery_radius_meters, minimum_order_minor, timezone "
                + "FROM store_fulfillment_settings WHERE store_id = ?" + (tenantId == null ? "" : " AND tenant_id = ?");
        List<StoreFulfillmentView> rows = tenantId == null
                ? jdbcTemplate.query(sql, (rs, row) -> new StoreFulfillmentView(
                        rs.getBoolean("delivery_enabled"), rs.getBoolean("pickup_enabled"),
                        (Integer) rs.getObject("delivery_radius_meters"), rs.getLong("minimum_order_minor"),
                        rs.getString("timezone")), storeId)
                : jdbcTemplate.query(sql, (rs, row) -> new StoreFulfillmentView(
                        rs.getBoolean("delivery_enabled"), rs.getBoolean("pickup_enabled"),
                        (Integer) rs.getObject("delivery_radius_meters"), rs.getLong("minimum_order_minor"),
                        rs.getString("timezone")), storeId, tenantId);
        return rows.stream().findFirst().orElse(null);
    }

    private List<StoreHourView> loadHours(UUID storeId, UUID tenantId) {
        String sql = "SELECT day_of_week, opens_at, closes_at, is_closed FROM store_hours WHERE store_id = ?"
                + (tenantId == null ? "" : " AND tenant_id = ?") + " ORDER BY day_of_week";
        return tenantId == null
                ? jdbcTemplate.query(sql, (rs, row) -> hourRow(rs), storeId)
                : jdbcTemplate.query(sql, (rs, row) -> hourRow(rs), storeId, tenantId);
    }

    private UUID requireStoreId(UUID tenantId) {
        List<UUID> stores = jdbcTemplate.query(
                "SELECT id FROM stores WHERE tenant_id = ?", (rs, row) -> rs.getObject(1, UUID.class), tenantId);
        if (stores.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found.");
        }
        return stores.getFirst();
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

    private static void validateFulfillment(StoreFulfillmentRequest request) {
        if (!request.deliveryEnabled() && !request.pickupEnabled()) {
            throw new IllegalArgumentException("At least one fulfillment method must be enabled.");
        }
        if (request.deliveryEnabled()
                && (request.deliveryRadiusMeters() == null || request.deliveryRadiusMeters() < 100
                        || request.deliveryRadiusMeters() > 50000)) {
            throw new IllegalArgumentException("Delivery radius must be between 100 and 50000 meters.");
        }
        if (!request.deliveryEnabled() && request.deliveryRadiusMeters() != null) {
            throw new IllegalArgumentException("Delivery radius must be omitted when delivery is disabled.");
        }
        try {
            ZoneId.of(request.timezone());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("A valid IANA timezone is required.", exception);
        }
    }

    private static void validateHours(StoreHoursRequest request) {
        if (request.hours().size() != 7) {
            throw new IllegalArgumentException("Exactly seven weekday entries are required.");
        }
        Set<Integer> days = new HashSet<>();
        for (StoreHourRequest hour : request.hours()) {
            if (!days.add(hour.dayOfWeek())) {
                throw new IllegalArgumentException("Each ISO weekday can appear only once.");
            }
            if (hour.closed()) {
                if (hour.opensAt() != null || hour.closesAt() != null) {
                    throw new IllegalArgumentException("Closed days cannot include opening or closing times.");
                }
            } else if (hour.opensAt() == null || hour.closesAt() == null
                    || !hour.closesAt().isAfter(hour.opensAt())) {
                throw new IllegalArgumentException("Open days require a valid same-day time window.");
            }
        }
        if (days.size() != 7) {
            throw new IllegalArgumentException("Every ISO weekday from 1 through 7 must be supplied.");
        }
    }

    private static StoreAddressView addressRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StoreAddressView(rs.getString("address_line1"), rs.getString("address_line2"),
                rs.getString("landmark"), rs.getString("locality"), rs.getString("city"), rs.getString("state"),
                rs.getString("postal_code"), rs.getDouble("latitude"), rs.getDouble("longitude"));
    }

    private static StoreHourView hourRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StoreHourView(rs.getInt("day_of_week"), localTime(rs.getObject("opens_at")),
                localTime(rs.getObject("closes_at")), rs.getBoolean("is_closed"));
    }

    private static LocalTime localTime(Object time) {
        if (time == null) {
            return null;
        }
        if (time instanceof java.sql.Time sqlTime) {
            return sqlTime.toLocalTime();
        }
        return (LocalTime) time;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
