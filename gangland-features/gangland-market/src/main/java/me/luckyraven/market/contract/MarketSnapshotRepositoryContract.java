package me.luckyraven.market.contract;

import me.luckyraven.market.snapshot.DailySnapshot;

import java.time.LocalDate;
import java.util.List;

public interface MarketSnapshotRepositoryContract {

	void save(DailySnapshot snapshot);

	List<DailySnapshot> history(String itemId, int days);

	/**
	 * Delete every snapshot strictly older than the given date.
	 */
	void pruneOlderThan(LocalDate cutoff);
}
