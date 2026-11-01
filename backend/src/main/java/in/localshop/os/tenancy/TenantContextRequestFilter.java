package in.localshop.os.tenancy;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Installs only a typed context placed on the request by trusted authentication code. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantContextRequestFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATED_CONTEXT_ATTRIBUTE =
            TenantContextRequestFilter.class.getName() + ".authenticatedContext";

    private final TenantContextHolder contextHolder;

    public TenantContextRequestFilter(TenantContextHolder contextHolder) {
        this.contextHolder = contextHolder;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Object requestContext = request.getAttribute(AUTHENTICATED_CONTEXT_ATTRIBUTE);
        if (requestContext == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!(requestContext instanceof TenantExecutionContext tenantContext)) {
            throw new IllegalStateException("Trusted authentication code supplied an invalid tenant context.");
        }

        try (TenantContextHolder.Scope ignored = contextHolder.install(tenantContext)) {
            filterChain.doFilter(request, response);
        }
    }
}
