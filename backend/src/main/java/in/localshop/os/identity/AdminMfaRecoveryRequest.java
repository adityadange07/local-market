package in.localshop.os.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminMfaRecoveryRequest(
        @NotBlank @Size(max = 64) String recoveryCode) { }
