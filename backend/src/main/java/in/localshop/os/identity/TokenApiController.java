package in.localshop.os.identity;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/auth/tokens")
public class TokenApiController {

    private final AuthTokenService authTokenService;

    public TokenApiController(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authTokenService.refresh(request.refreshToken());
    }

    @PostMapping("/revoke")
    public ResponseEntity<Void> revoke(@Valid @RequestBody RefreshTokenRequest request) {
        authTokenService.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/revoke-device")
    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'customer.devices.manage')")
    public ResponseEntity<Void> revokeDevice(
            @Valid @RequestBody RevokeDeviceSessionsRequest request) {
        authTokenService.revokeDeviceSessions(request.deviceId());
        return ResponseEntity.noContent().build();
    }
}
