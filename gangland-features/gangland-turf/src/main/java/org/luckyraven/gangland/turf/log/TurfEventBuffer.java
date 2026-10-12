package org.luckyraven.gangland.turf.log;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory "while you were away" memory (TF-37): per gang the last {@value #CAPACITY} rendered event lines and the
 * last 300 income payouts, and per player the time of his last quit. Nothing is saved, so a restart empties it and a
 * join with no quit stamp falls back to the newest lines. Every method is synchronized: payouts and events run on the
 * main thread, but a quit can race a join.
 */
// ponytail: one lock for the whole buffer; per-gang locks if the payout loop ever shows up in a profile.
public final class TurfEventBuffer {

	/** Event lines kept per gang. */
	public static final int CAPACITY = 10;

	private static final int INCOME_CAPACITY = 300;

	/** One rendered event line and the time it happened (epoch millis). */
	public record Entry(long at, String line) {
	}

	private record Payout(long at, BigDecimal amount) {
	}

	private final Map<Integer, Deque<Entry>>  events = new HashMap<>();
	private final Map<Integer, Deque<Payout>> income = new HashMap<>();
	private final Map<UUID, Long>             quits  = new HashMap<>();

	/** Records one event line for a gang, dropping the oldest once {@link #CAPACITY} lines are held. */
	public synchronized void record(int gangId, long at, String line) {
		Deque<Entry> ring = events.computeIfAbsent(gangId, id -> new ArrayDeque<>());
		ring.addLast(new Entry(at, line));
		while (ring.size() > CAPACITY) {
			ring.removeFirst();
		}
	}

	/** The newest {@code max} event lines of a gang, oldest first. */
	public synchronized List<Entry> latest(int gangId, int max) {
		return since(gangId, Long.MIN_VALUE, max);
	}

	/** The newest {@code max} event lines of a gang recorded strictly after {@code since}, oldest first. */
	public synchronized List<Entry> since(int gangId, long since, int max) {
		Deque<Entry> ring = events.get(gangId);
		if (ring == null) {
			return List.of();
		}
		List<Entry> after = new ArrayList<>();
		for (Entry entry : ring) {
			if (entry.at() > since) {
				after.add(entry);
			}
		}
		int from = Math.max(0, after.size() - Math.max(0, max));
		return List.copyOf(after.subList(from, after.size()));
	}

	/** Records one payout for a gang, keeping the last 300. */
	public synchronized void recordIncome(int gangId, long at, BigDecimal amount) {
		Deque<Payout> log = income.computeIfAbsent(gangId, id -> new ArrayDeque<>());
		log.addLast(new Payout(at, amount));
		while (log.size() > INCOME_CAPACITY) {
			log.removeFirst();
		}
	}

	/** The income a gang was paid strictly after {@code since}; zero when nothing was paid. */
	public synchronized BigDecimal incomeSince(int gangId, long since) {
		BigDecimal total = BigDecimal.ZERO;
		Deque<Payout> log = income.get(gangId);
		if (log == null) {
			return total;
		}
		for (Payout payout : log) {
			if (payout.at() > since) {
				total = total.add(payout.amount());
			}
		}
		return total;
	}

	/** Stamps the time a player quit. */
	public synchronized void markQuit(UUID player, long at) {
		quits.put(player, at);
	}

	/** The time a player last quit, or {@code 0} when unknown (never quit since this process started). */
	public synchronized long lastQuit(UUID player) {
		return quits.getOrDefault(player, 0L);
	}

	/** Drops a deleted gang's lines and income. */
	public synchronized void forgetGang(int gangId) {
		events.remove(gangId);
		income.remove(gangId);
	}
}
