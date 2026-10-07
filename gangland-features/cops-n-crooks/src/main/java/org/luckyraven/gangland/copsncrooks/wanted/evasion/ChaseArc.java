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

	long                        startedAt;
	long                        lastHotAt;
	long                        lastLostAt;
	long                        offlineAt;
	int                         peak;
	int                         respots;
	int                         quits;
	boolean                     searched;
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
}
