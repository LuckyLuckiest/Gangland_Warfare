package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.wanted.config.AutoSettings;
import org.luckyraven.gangland.copsncrooks.wanted.heat.CrimeRecord;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.crime.Crimes;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Per-player {@link ChaseArc}s plus the memory of recently ended crime chases. Memory only; main thread only.
 *
 * @since 0.15.2
 */
public final class ChaseArcs {

	/** An arc whose player has been offline longer than this is dropped; he comes back as a RESTORE start. */
	static final long OFFLINE_KEEP_MS = 30L * 60_000L;
	static final int  RECENT_CAP      = 8;
	/** How far before the start the triggering crime may be stamped: the start listeners run after the ledger. */
	static final long START_SLACK_MS  = 1000L;

	private final LongSupplier clock;

	private final Map<UUID, ChaseArc>         arcs   = new HashMap<>();
	private final Map<UUID, ArrayDeque<Long>> recent = new HashMap<>();

	public ChaseArcs(LongSupplier clock) {
		this.clock = clock;
	}

	/** The arcs' clock, so callers stamp on the same timeline. */
	public long now() {
		return clock.getAsLong();
	}

	public boolean has(UUID id) {
		return arcs.containsKey(id);
	}

	public @Nullable ChaseArc arc(UUID id) {
		return arcs.get(id);
	}

	/** A new chase. A RESTORE start with no arc (restart, or offline too long) counts as one quit. */
	public void start(UUID id, WantedCause cause, int level) {
		prune();
		long     now = clock.getAsLong();
		ChaseArc arc = new ChaseArc(cause);
		arc.begunAt   = now;
		arc.startedAt = now;
		arc.lastHotAt = now;
		arc.peak      = level;
		if (cause == WantedCause.RESTORE) arc.quits = 1;
		arcs.put(id, arc);
	}

	/** The player is back: shift every stamp by the time he was away so chase and quiet exclude it. */
	public void restore(UUID id) {
		ChaseArc arc = arcs.get(id);
		if (arc == null || arc.offlineAt == 0) return;

		long gap = clock.getAsLong() - arc.offlineAt;
		arc.startedAt += gap;
		arc.lastHotAt += gap;
		if (arc.lastLostAt != 0) arc.lastLostAt += gap;
		arc.offlineAt = 0;
	}

	/** A crime the ledger keeps, or a star raise. */
	public void hot(UUID id) {
		ChaseArc arc = arcs.get(id);
		if (arc != null) arc.lastHotAt = clock.getAsLong();
	}

	public void peak(UUID id, int level) {
		ChaseArc arc = arcs.get(id);
		if (arc != null) arc.peak = Math.max(arc.peak, level);
	}

	public void searchStarted(UUID id) {
		ChaseArc arc = arcs.get(id);
		if (arc != null) arc.searched = true;
	}

	/** A new SEEN track: a respot only when the squad had lost him first. */
	public void seen(UUID id) {
		ChaseArc arc = arcs.get(id);
		if (arc != null && arc.searched) {
			arc.respots++;
			arc.searched = false;
		}
	}

	public void lost(UUID id) {
		ChaseArc arc = arcs.get(id);
		if (arc != null) arc.lastLostAt = clock.getAsLong();
	}

	public void quit(UUID id) {
		ChaseArc arc = arcs.get(id);
		if (arc == null) return;

		arc.quits++;
		arc.offlineAt = clock.getAsLong();
	}

	/** The chase is over; a chase with a crime is remembered for {@code recentEnds}. */
	public void end(UUID id, boolean hadCrime) {
		ChaseArc arc = arcs.remove(id);
		if (arc == null || !hadCrime) return;

		ArrayDeque<Long> ends = recent.computeIfAbsent(id, k -> new ArrayDeque<>());
		ends.addLast(clock.getAsLong());
		while (ends.size() > RECENT_CAP) ends.removeFirst();
	}

	/** Drops arcs of players offline over 30 minutes. */
	public void prune() {
		long now = clock.getAsLong();
		arcs.values().removeIf(arc -> arc.offlineAt != 0 && now - arc.offlineAt > OFFLINE_KEEP_MS);
	}

	/** The planner's view of the chase, or null when no arc exists. */
	public @Nullable AutoDrop.ChaseView view(UUID id, List<CrimeRecord> crimes, AutoSettings settings) {
		ChaseArc arc = arcs.get(id);
		if (arc == null) return null;

		crimes = onChase(arc, crimes, settings);
		long    now     = arc.offlineAt != 0 ? arc.offlineAt : clock.getAsLong();
		long    cutoff  = crimes.isEmpty() ? 0 : crimes.get(0).at() + settings.openingSeconds() * 1000L;
		int     opening = 0;
		boolean kill    = false;
		for (CrimeRecord crime : crimes) {
			if (crime.at() <= cutoff) opening++;
			if (Crimes.KILL_COP.equals(crime.crimeId())) kill = true;
		}

		return new AutoDrop.ChaseView(crimes.size(), opening, kill, arc.peak, now - arc.startedAt,
		                              now - arc.lastHotAt, arc.quits, arc.respots,
		                              recentEnds(id, settings.repeatWindowMinutes()));
	}

	/** Whether any of the ledger's crimes is on this player's current chase; false with no arc. */
	public boolean hadCrime(UUID id, List<CrimeRecord> crimes, AutoSettings settings) {
		ChaseArc arc = arcs.get(id);
		return arc != null && !onChase(arc, crimes, settings).isEmpty();
	}

	/**
	 * The ledger keeps sub-threshold crimes with no decay, so its list can hold a crime from long before this chase. A
	 * crime counts only from {@code Opening_Seconds} before the start on: the build-up that raised the first star. The
	 * slack keeps the triggering crime, stamped a few ms before the start listeners run, even at {@code Opening_Seconds: 0}.
	 */
	private static List<CrimeRecord> onChase(ChaseArc arc, List<CrimeRecord> crimes, AutoSettings settings) {
		long from = arc.begunAt - settings.openingSeconds() * 1000L - START_SLACK_MS;
		if (crimes.isEmpty() || crimes.get(0).at() >= from) return crimes;

		return crimes.stream().filter(crime -> crime.at() >= from).toList();
	}

	/** Crime chases this player ended inside the window. */
	public int recentEnds(UUID id, int windowMinutes) {
		ArrayDeque<Long> ends = recent.get(id);
		if (ends == null) return 0;

		long limit = clock.getAsLong() - windowMinutes * 60_000L;
		ends.removeIf(at -> at < limit);
		if (ends.isEmpty()) recent.remove(id);
		return ends.size();
	}

	public void stashPending(UUID id, AutoDrop.DropPlan plan) {
		ChaseArc arc = arcs.get(id);
		if (arc != null) arc.pending = plan;
	}

	public @Nullable AutoDrop.DropPlan takePending(UUID id) {
		ChaseArc arc = arcs.get(id);
		if (arc == null) return null;

		AutoDrop.DropPlan plan = arc.pending;
		arc.pending = null;
		return plan;
	}
}
