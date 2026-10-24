package org.luckyraven.gangland.copsncrooks.wanted.report;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.detainment.ArrestOrigin;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages.Key;
import org.luckyraven.gangland.copsncrooks.wanted.config.PursuitReportSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArc;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.file.configuration.Settings;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The pursuit report of a finished chase, as an ordered line list.
 *
 * @since 0.21.0
 */
public final class PursuitReport {

	// ponytail: the star row is always 5 wide, the shipped max; a larger Max_Level would need the wanted passed in.
	private static final int MAX_STARS = 5;

	private PursuitReport() {
	}

	/** The ending's message key: DECAY/EVASION Escaped, ARREST by origin, DEATH Wasted, BRIBE/CONTACT Paid off, else Cleared. */
	public static Key ending(WantedCause cause, @Nullable ArrestOrigin origin) {
		return switch (cause) {
			case DECAY, EVASION -> Key.ENDING_ESCAPED;
			case ARREST -> arrestEnding(origin);
			case DEATH -> Key.ENDING_WASTED;
			case BRIBE, CONTACT -> Key.ENDING_PAID_OFF;
			default -> Key.ENDING_CLEARED;
		};
	}

	private static Key arrestEnding(@Nullable ArrestOrigin origin) {
		if (origin == ArrestOrigin.SURRENDERED) {
			return Key.ENDING_SURRENDERED;
		}
		if (origin == ArrestOrigin.BUSTED_DOWNED) {
			return Key.ENDING_BUSTED;
		}
		return Key.ENDING_ARRESTED;
	}

	/**
	 * Header, Ending, Duration, Peak, Cops_Down, Busts, Cost; {@code heat} is the sum of the chase's ledger crimes. Later
	 * releases append lines (Clean getaway XP).
	 */
	public static List<String> lines(ChaseArc arc, WantedCause cause, @Nullable ArrestOrigin origin, double heat,
	                                 PursuitReportSettings settings, WantedMessages messages, long now) {
		long end     = arc.offlineAt() != 0 ? arc.offlineAt() : now;
		int  seconds = (int) Math.max(0, (end - arc.startedAt()) / 1000L);
		double cost  = settings.perCopDown() * arc.copsDown() + settings.perHeat() * heat;
		// ponytail: whole-dollar grouped figure, not Settings.formatAmount (compact 6.5K) - the rundown shows the full sum.

		return List.of(
				messages.format(Key.REPORT_HEADER, Map.of()),
				messages.format(Key.REPORT_ENDING,
				                Map.of("ending", messages.format(ending(cause, origin), Map.of()))),
				messages.format(Key.REPORT_DURATION, Map.of("time", WantedMessages.duration(seconds))),
				messages.format(Key.REPORT_PEAK, Map.of("stars", Wanted.buildStars(arc.peak(), MAX_STARS))),
				messages.format(Key.REPORT_COPS_DOWN, Map.of("count", String.valueOf(arc.copsDown()))),
				messages.format(Key.REPORT_BUSTS, Map.of("count", String.valueOf(arc.busts()))),
				messages.format(Key.REPORT_COST, Map.of("money_symbol", Settings.getMoneySymbol(), "amount",
				                                        String.format(Locale.ROOT, "%,d", Math.round(cost)))));
	}
}
