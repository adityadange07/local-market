package in.localshop.os.shops;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record StoreHourRequest(
        @Min(1) @Max(7) int dayOfWeek,
        LocalTime opensAt,
        LocalTime closesAt,
        boolean closed) { }
