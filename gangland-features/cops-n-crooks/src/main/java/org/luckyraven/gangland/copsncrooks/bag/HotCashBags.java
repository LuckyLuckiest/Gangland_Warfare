package org.luckyraven.gangland.copsncrooks.bag;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.database.HotCashBagRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-plugin store of every player's {@link BagEntry} (cents in {@link BigDecimal}); no Bukkit, no static. Entries are
 * zeroed, never removed, so a zeroed bag overwrites its saved row.
 *
 * @since 0.20.0
 */
public final class HotCashBags {

	private final @Nullable HotCashBagRepository repository;
	// ponytail: mutators are synchronized on this store (one lock for all players); per-player locks if contention shows.
	private final Map<UUID, BagEntry> rows = new ConcurrentHashMap<>();

	/** @param repository the saved rows, or null for an in-memory store (tests). */
	public HotCashBags(@Nullable HotCashBagRepository repository) {
		this.repository = repository;
	}

	/** Replaces the in-memory rows with the repository's (module enable). */
	public synchronized void load() {
		if (repository == null) {
			return;
		}
		for (BagEntry entry : repository.loadAll()) {
			put(entry);
		}
	}

	/** Saves every row through the repository; a no-op without one. */
	public void flush() {
		if (repository == null) {
			return;
		}
		repository.saveAll(all());
	}

	/** Puts a row (used by load and by tests that seed a state). */
	public synchronized void put(BagEntry entry) {
		rows.put(entry.player(), entry);
	}

	/** Every row, zeroed ones included: the repository data supplier. */
	public Collection<BagEntry> all() {
		return new ArrayList<>(rows.values());
	}

	/** The row for a player, created zeroed when absent. */
	public BagEntry entry(UUID player) {
		return rows.computeIfAbsent(player, id -> new BagEntry(id, BigDecimal.ZERO, BigDecimal.ZERO, 0L,
		                                                       BigDecimal.ZERO, BigDecimal.ZERO));
	}

	public BigDecimal amount(UUID player) {
		BagEntry entry = rows.get(player);
		return entry == null ? BigDecimal.ZERO : entry.amount();
	}

	public BigDecimal chaseTaken(UUID player) {
		BagEntry entry = rows.get(player);
		return entry == null ? BigDecimal.ZERO : entry.chaseTaken();
	}

	/**
	 * Adds to the bag and the chase counter.
	 *
	 * @param chaseCap most one chase may bag; 0 = no cap
	 *
	 * @return false (nothing added) when {@code chaseTaken + amount} would exceed a non-zero cap
	 */
	public synchronized boolean add(UUID player, BigDecimal amount, BigDecimal chaseCap) {
		BagEntry entry = entry(player);
		BigDecimal chase = entry.chaseTaken().add(amount);
		if (chaseCap.signum() > 0 && chase.compareTo(chaseCap) > 0) {
			return false;
		}
		entry.amount(entry.amount().add(amount));
		entry.chaseTaken(chase);
		return true;
	}

	/** Returns the whole bag and zeroes it and the chase counter FIRST. */
	public synchronized BigDecimal takeAll(UUID player) {
		BagEntry entry = entry(player);
		BigDecimal taken = entry.amount();
		entry.amount(BigDecimal.ZERO);
		entry.chaseTaken(BigDecimal.ZERO);
		return taken;
	}

	/** Returns the whole bag and zeroes it; the chase counter stays (the fence). */
	public synchronized BigDecimal takeAllKeepChase(UUID player) {
		BagEntry entry = entry(player);
		BigDecimal taken = entry.amount();
		entry.amount(BigDecimal.ZERO);
		return taken;
	}

	/** Removes {@code amount} (at most the bag) from the bag; the chase counter stays. */
	public synchronized void debit(UUID player, BigDecimal amount) {
		BagEntry entry = entry(player);
		entry.amount(entry.amount().subtract(amount).max(BigDecimal.ZERO));
	}

	/**
	 * What a getaway may still bank on {@code epochDay}: the smaller of {@code wanted} and (cap 0 = no limit, else
	 * {@code dailyCap - bankedToday}, never below 0); a new day starts the allowance over.
	 */
	public BigDecimal allowance(UUID player, long epochDay, BigDecimal dailyCap, BigDecimal wanted) {
		if (dailyCap.signum() == 0) {
			return wanted;
		}
		BagEntry entry = rows.get(player);
		BigDecimal banked = entry == null || entry.bankedDay() != epochDay ? BigDecimal.ZERO : entry.bankedToday();
		return wanted.min(dailyCap.subtract(banked).max(BigDecimal.ZERO));
	}

	/** Adds {@code banked} to the day's used allowance, resetting it first when {@code epochDay} is a new day. */
	public synchronized void recordBanked(UUID player, long epochDay, BigDecimal banked) {
		BagEntry entry = entry(player);
		BigDecimal today = entry.bankedDay() == epochDay ? entry.bankedToday() : BigDecimal.ZERO;
		entry.banked(epochDay, today.add(banked));
	}

	public BigDecimal seized(UUID player) {
		BagEntry entry = rows.get(player);
		return entry == null ? BigDecimal.ZERO : entry.seized();
	}

	/** Stores the last seizure (0 included: an arrest with an empty bag clears the paperwork line). */
	public synchronized void setSeized(UUID player, BigDecimal amount) {
		entry(player).seized(amount);
	}
}
