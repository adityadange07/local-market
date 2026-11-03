package in.localshop.os.shops;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StoreAddressRequest(
        @NotBlank @Size(max = 180) String addressLine1,
        @Size(max = 180) String addressLine2,
        @Size(max = 120) String landmark,
        @NotBlank @Size(max = 120) String locality,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String postalCode,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) { }
