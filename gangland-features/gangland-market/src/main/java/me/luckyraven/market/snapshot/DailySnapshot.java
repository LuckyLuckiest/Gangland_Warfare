package me.luckyraven.market.snapshot;

import java.time.LocalDate;

public record DailySnapshot(
		String itemId,
		LocalDate snapshotDate,
		double open,
		double high,
		double low,
		double close,
		long volume
) {
}
