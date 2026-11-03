package in.localshop.os.shops;

import java.time.LocalTime;

public record StoreHourView(int dayOfWeek, LocalTime opensAt, LocalTime closesAt, boolean closed) { }
