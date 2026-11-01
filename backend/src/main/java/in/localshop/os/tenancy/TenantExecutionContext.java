package in.localshop.os.tenancy;

import java.util.Objects;
import java.util.UUID;

/** Backend-derived identity and scope for one request or background operation. */
public record TenantExecutionContext(UUID userId, UUID tenantId, TenantActorScope actorScope) {

    public TenantExecutionContext {
        Objects.requireNonNull(actorScope, "actorScope");

        switch (actorScope) {
            case PUBLIC -> {
                if (userId != null || tenantId != null) {
                    throw new IllegalArgumentException("Public context cannot contain a user or tenant.");
                }
            }
            case CUSTOMER -> {
                Objects.requireNonNull(userId, "userId");
                if (tenantId != null) {
                    throw new IllegalArgumentException("Customer context is not bound to one shop tenant.");
                }
            }
            case IDENTITY_AUTHENTICATION -> {
                if (tenantId != null) {
                    throw new IllegalArgumentException("Identity authentication is not tenant-scoped.");
                }
            }
            case IDENTITY_PROVISIONING -> {
                Objects.requireNonNull(userId, "userId");
                if (tenantId != null) {
                    throw new IllegalArgumentException("Identity provisioning is not tenant-scoped.");
                }
            }
            case SELLER, TENANT_ONBOARDING -> {
                Objects.requireNonNull(userId, "userId");
                Objects.requireNonNull(tenantId, "tenantId");
            }
            case PLATFORM_ADMIN -> Objects.requireNonNull(userId, "userId");
            case SYSTEM -> {
                // Scheduled work may be global or scoped to one tenant.
            }
        }
    }
}
