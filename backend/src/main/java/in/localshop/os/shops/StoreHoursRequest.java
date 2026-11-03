package in.localshop.os.shops;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record StoreHoursRequest(@NotEmpty @Size(min = 7, max = 7) List<@Valid StoreHourRequest> hours) { }
