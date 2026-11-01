package in.localshop.os.tenancy;

import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class TenantContextHolder {

    private final ThreadLocal<TenantExecutionContext> current = new ThreadLocal<>();

    public Optional<TenantExecutionContext> current() {
        return Optional.ofNullable(current.get());
    }

    public TenantExecutionContext requireCurrent() {
        TenantExecutionContext context = current.get();
        if (context == null) {
            throw new IllegalStateException("No authenticated tenant execution context is installed.");
        }
        return context;
    }

    public Scope install(TenantExecutionContext context) {
        TenantExecutionContext previous = current.get();
        current.set(context);
        return () -> {
            if (previous == null) {
                current.remove();
            } else {
                current.set(previous);
            }
        };
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
