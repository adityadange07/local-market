package in.localshop.os.identity;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;

public record AdminMfaCodeRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) { }
