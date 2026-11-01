package in.localshop.os.identity;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/admin/mfa")
public class AdminMfaController {

    private final AdminMfaService mfaService;

    public AdminMfaController(AdminMfaService mfaService) {
        this.mfaService = mfaService;
    }

    @PostMapping("/enrollment/start")
    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'platform.config.manage')")
    public AdminMfaEnrollmentStartResponse startEnrollment(@AuthenticationPrincipal Jwt principal) {
        return mfaService.startEnrollment(userId(principal));
    }

    @PostMapping("/enrollment/verify")
    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'platform.config.manage')")
    public AdminMfaEnrollmentCompleteResponse completeEnrollment(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody AdminMfaCodeRequest request) {
        return mfaService.completeEnrollment(userId(principal), request.code());
    }

    @PostMapping("/verify")
    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'platform.config.manage')")
    public AdminMfaTokenResponse verify(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody AdminMfaCodeRequest request) {
        return mfaService.verify(userId(principal), request.code());
    }

    @PostMapping("/recover")
    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'platform.config.manage')")
    public AdminMfaTokenResponse recover(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody AdminMfaRecoveryRequest request) {
        return mfaService.recover(userId(principal), request.recoveryCode());
    }

    private static UUID userId(Jwt principal) {
        return UUID.fromString(principal.getSubject());
    }
}
