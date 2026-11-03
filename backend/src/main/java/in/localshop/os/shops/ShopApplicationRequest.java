package in.localshop.os.shops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShopApplicationRequest(
        @NotBlank @Size(min = 2, max = 160) String accountName,
        @NotBlank @Size(min = 2, max = 160) String storeName,
        @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") String slug) { }
