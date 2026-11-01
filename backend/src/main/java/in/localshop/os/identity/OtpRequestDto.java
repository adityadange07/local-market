package in.localshop.os.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OtpRequestDto(
        @NotBlank @Size(max = 32) String phone,
        @NotNull OtpPurpose purpose,
        @NotBlank @Size(min = 16, max = 128) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String deviceId) { }
