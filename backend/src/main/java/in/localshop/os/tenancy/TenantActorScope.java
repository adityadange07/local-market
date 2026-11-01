package in.localshop.os.tenancy;

public enum TenantActorScope {
    PUBLIC("public"),
    CUSTOMER("customer"),
    IDENTITY_AUTHENTICATION("identity_authentication"),
    IDENTITY_PROVISIONING("identity_provisioning"),
    SELLER("seller"),
    PLATFORM_ADMIN("platform_admin"),
    TENANT_ONBOARDING("tenant_onboarding"),
    SYSTEM("system");

    private final String databaseValue;

    TenantActorScope(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
