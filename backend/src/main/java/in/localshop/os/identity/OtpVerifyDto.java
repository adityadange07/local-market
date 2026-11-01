package in.localshop.os.identity;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OtpVerifyDto(
        @NotNull UUID challengeId,
        @NotBlank @Size(max = 32) String phone,
        @NotNull OtpPurpose purpose,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code,
        @NotBlank @Size(min = 16, max = 128) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String deviceId) { }
