package org.luckyraven.gangland.copsncrooks.bag;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.database.HotCashBagRepository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;

/**
 * Per-plugin store of every player's {@link BagEntry} (cents in {@link BigDecimal}); no Bukkit, no static. Entries are
 * zeroed, never removed, so a zeroed bag overwrites its saved row.
 *
 * @since 0.20.0
 */
public final class HotCashBags {

	private final @Nullable HotCashBagRepository repository;

	/** @param repository the saved rows, or null for an in-memory store (tests). */
	public HotCashBags(@Nullable HotCashBagRepository repository) {
		this.repository = repository;
	}

	/** Replaces the in-memory rows with the repository's (module enable). */
	public void load() {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Saves every row through the repository; a no-op without one. */
	public void flush() {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Puts a row (used by load and by tests that seed a state). */
	public void put(BagEntry entry) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Every row, zeroed ones included: the repository data supplier. */
	public Collection<BagEntry> all() {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The row for a player, created zeroed when absent. */
	public BagEntry entry(UUID player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public BigDecimal amount(UUID player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public BigDecimal chaseTaken(UUID player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/**
	 * Adds to the bag and the chase counter.
	 *
	 * @param chaseCap most one chase may bag; 0 = no cap
	 *
	 * @return false (nothing added) when {@code chaseTaken + amount} would exceed a non-zero cap
	 */
	public boolean add(UUID player, BigDecimal amount, BigDecimal chaseCap) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Returns the whole bag and zeroes it and the chase counter FIRST. */
	public BigDecimal takeAll(UUID player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Returns the whole bag and zeroes it; the chase counter stays (the fence). */
	public BigDecimal takeAllKeepChase(UUID player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Removes {@code amount} (at most the bag) from the bag; the chase counter stays. */
	public void debit(UUID player, BigDecimal amount) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/**
	 * What a getaway may still bank on {@code epochDay}: the smaller of {@code wanted} and (cap 0 = no limit, else
	 * {@code dailyCap - bankedToday}, never below 0); a new day starts the allowance over.
	 */
	public BigDecimal allowance(UUID player, long epochDay, BigDecimal dailyCap, BigDecimal wanted) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Adds {@code banked} to the day's used allowance, resetting it first when {@code epochDay} is a new day. */
	public void recordBanked(UUID player, long epochDay, BigDecimal banked) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public BigDecimal seized(UUID player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Stores the last seizure (0 included: an arrest with an empty bag clears the paperwork line). */
	public void setSeized(UUID player, BigDecimal amount) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
