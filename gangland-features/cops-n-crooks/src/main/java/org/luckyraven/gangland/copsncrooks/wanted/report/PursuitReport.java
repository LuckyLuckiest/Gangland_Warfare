package org.luckyraven.gangland.copsncrooks.wanted.report;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.detainment.ArrestOrigin;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.PursuitReportSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArc;
import org.luckyraven.gangland.core.wanted.WantedCause;

import java.util.List;

/**
 * The pursuit report of a finished chase, as an ordered line list.
 *
 * @since 0.21.0
 */
public final class PursuitReport {

	private PursuitReport() {
	}

	/** The ending's message key: DECAY/EVASION Escaped, ARREST by origin, DEATH Wasted, BRIBE/CONTACT Paid off, else Cleared. */
	public static WantedMessages.Key ending(WantedCause cause, @Nullable ArrestOrigin origin) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/**
	 * Header, Ending, Duration, Peak, Cops_Down, Busts, Cost; {@code heat} is the sum of the chase's ledger crimes. Later
	 * releases append lines (Clean getaway XP).
	 */
	public static List<String> lines(ChaseArc arc, WantedCause cause, @Nullable ArrestOrigin origin, double heat,
	                                 PursuitReportSettings settings, WantedMessages messages, long now) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
