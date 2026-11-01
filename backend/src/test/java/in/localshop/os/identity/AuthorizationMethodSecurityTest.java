package in.localshop.os.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@SpringJUnitConfig(AuthorizationMethodSecurityTest.SecurityTestConfiguration.class)
class AuthorizationMethodSecurityTest {

    @Autowired
    private AuthTokenService authTokenService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void directServiceInvocationIsDeniedWhenPermissionEvaluatorRejectsTheActor() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertThatThrownBy(() -> authTokenService.revokeDeviceSessions("device_0123456789"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class SecurityTestConfiguration {

        @Bean
        PermissionAuthorization permissionAuthorization() {
            PermissionAuthorization evaluator = mock(PermissionAuthorization.class);
            when(evaluator.hasGlobal(any(), eq("customer.devices.manage"))).thenReturn(false);
            return evaluator;
        }

        @Bean
        AuthTokenService authTokenService() {
            PlatformTransactionManager transactionManager = new DataSourceTransactionManager(mock(DataSource.class));
            return new AuthTokenService(
                    mock(JdbcTemplate.class), mock(JwtEncoder.class), mock(java.security.SecureRandom.class),
                    Clock.systemUTC(), mock(in.localshop.os.tenancy.TenantContextHolder.class),
                    mock(in.localshop.os.tenancy.TenantDatabaseContext.class), transactionManager,
                    Duration.ofMinutes(10), Duration.ofDays(30),
                    "https://test.local", "local-shop-os-api");
        }
    }
}
