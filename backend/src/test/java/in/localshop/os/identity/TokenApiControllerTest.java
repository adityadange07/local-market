package in.localshop.os.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TokenApiControllerTest {

    private AuthTokenService authTokenService;
    private TokenApiController controller;

    @BeforeEach
    void setUp() {
        authTokenService = mock(AuthTokenService.class);
        controller = new TokenApiController(authTokenService);
    }

    @Test
    void refreshReturnsRotatedTokenResponse() {
        TokenResponse rotated = new TokenResponse("new-access", "new-refresh", "Bearer", 600,
                Instant.parse("2026-12-01T00:00:00Z"));
        when(authTokenService.refresh("old-refresh")).thenReturn(rotated);

        TokenResponse response = controller.refresh(new RefreshTokenRequest("old-refresh"));

        assertThat(response).isEqualTo(rotated);
        verify(authTokenService).refresh("old-refresh");
    }

    @Test
    void revokeReturnsNoContentAndDelegatesToSessionFamilyService() {
        var response = controller.revoke(new RefreshTokenRequest("presented-refresh"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authTokenService).revoke("presented-refresh");
    }
}
