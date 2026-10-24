package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.wanted.WantedCause;

/**
 * The facts of one player's chase that survive a SEEN track and a quit. Stamps are clock millis; {@code 0} means unset.
 * Main thread only.
 *
 * @since 0.15.2
 */
public final class ChaseArc {

	final WantedCause startCause;

	/** Clock millis at the start, never shifted by a restore: the cut-off against the ledger's own crime stamps. */
	long                        begunAt;
	long                        startedAt;
	long                        lastHotAt;
	long                        lastLostAt;
	long                        offlineAt;
	/** The last RESTORE start (rejoin), on the same timeline; 0 for a chase never restored. */
	long                        seededAt;
	/** Sum of the shifted offline gaps ({@link ChaseArcs#restore}). */
	long                        offlineTotal;
	int                         peak;
	int                         respots;
	int                         quits;
	boolean                     searched;
	/** A contact or bribe star took stars off mid-chase: the peak no longer says what was escaped. */
	boolean                     boughtDown;
	/** Cops this player killed on the chase (0.21.0 pursuit report). */
	int                         copsDown;
	/** Times a cop cuffed him on the chase (0.21.0). */
	int                         busts;
	/** The live breaking-news headline went out for this chase (0.21.0). */
	boolean                     newsSent;
	/** Stars raised with WantedCause.CRIME on this chase, drops clamp it (0.21.0). */
	int                         earned;
	/** Highest {@link #earned} this chase (0.21.0). */
	int                         earnedPeak;
	/** Clock millis when {@link #earned} last rose; 0 = never (0.21.0). */
	long                        earnedAt;
	/** A real cop sighting this chase (P-GET-5, 0.21.0). */
	boolean                     sighted;
	@Nullable AutoDrop.DropPlan pending;

	ChaseArc(WantedCause startCause) {
		this.startCause = startCause;
	}

	public WantedCause startCause() {
		return startCause;
	}

	public int peak() {
		return peak;
	}

	public boolean boughtDown() {
		return boughtDown;
	}

	public long startedAt() {
		return startedAt;
	}

	/** 0 until the squad first loses him. */
	public long lastLostAt() {
		return lastLostAt;
	}

	/** 0 while the player is online. */
	public long offlineAt() {
		return offlineAt;
	}

	/** Every offline gap the restores shifted out of this chase, summed. */
	public long offlineTotal() {
		return offlineTotal;
	}

	// 0.21.0
	public int copsDown() {
		return copsDown;
	}

	// 0.21.0
	public int busts() {
		return busts;
	}

	// 0.21.0
	public boolean newsSent() {
		return newsSent;
	}

	// 0.21.0 wave 2
	public int earned() {
		return earned;
	}

	// 0.21.0 wave 2
	public int earnedPeak() {
		return earnedPeak;
	}

	// 0.21.0 wave 2
	public long earnedAt() {
		return earnedAt;
	}

	// 0.21.0 wave 2
	public boolean sighted() {
		return sighted;
	}
}
