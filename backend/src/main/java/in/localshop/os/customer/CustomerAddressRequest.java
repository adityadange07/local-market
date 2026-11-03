package in.localshop.os.customer;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerAddressRequest(
        @NotBlank @Size(max = 50) String label,
        @NotBlank @Size(max = 120) String recipientName,
        @Pattern(regexp = "^$|^\\+[1-9][0-9]{1,14}$") String recipientPhone,
        @NotBlank @Size(max = 180) String addressLine1,
        @Size(max = 180) String addressLine2,
        @Size(max = 120) String landmark,
        @NotBlank @Size(max = 120) String locality,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String postalCode,
        Double latitude,
        Double longitude,
        boolean defaultAddress) {
    @AssertTrue(message = "Latitude and longitude must be provided together and within valid ranges.")
    public boolean validCoordinates() {
        if (latitude == null && longitude == null) return true;
        return latitude != null && longitude != null && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }
}
