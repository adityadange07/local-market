package in.localshop.os.shops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectShopApplicationRequest(@NotBlank @Size(min = 10, max = 500) String reason) { }
