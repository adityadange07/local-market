package in.localshop.os.tenancy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantDatabaseContext {

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextHolder contextHolder;

    public TenantDatabaseContext(JdbcTemplate jdbcTemplate, TenantContextHolder contextHolder) {
        this.jdbcTemplate = jdbcTemplate;
        this.contextHolder = contextHolder;
    }

    /**
     * Sets all RLS context values transaction-locally. Call at the start of a tenant-aware
     * transactional service method, before accessing a protected table.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyCurrent() {
        TenantExecutionContext context = contextHolder.requireCurrent();
        setConfig("app.tenant_id", context.tenantId() == null ? "" : context.tenantId().toString());
        setConfig("app.user_id", context.userId() == null ? "" : context.userId().toString());
        setConfig("app.actor_scope", context.actorScope().databaseValue());
    }

    private void setConfig(String setting, String value) {
        jdbcTemplate.queryForObject(
                "SELECT set_config('" + setting + "', ?, true)", String.class, value);
    }
}
