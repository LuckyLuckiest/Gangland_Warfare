package org.luckyraven.gangland.copsncrooks.wanted.getaway;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArc;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.data.gang.GangMembership;

import java.time.ZoneId;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Getaway XP (0.21.0 wave 2): an evasion end pays XP by the earned peak, half of it to the wanted crew. Cooldown and
 * daily tallies are in memory. Main thread only.
 *
 * @since 0.21.0
 */
@CustomLog
public class GetawayService {

	/** What {@link #award} did. */
	public record Result(int granted, Reason reason) { }

	public enum Reason {
		PAID, CAPPED, LOW_PEAK, NO_SEARCH, COOLDOWN, DISABLED, NOT_EVASION
	}

	public GetawayService(ChaseArcs arcs, ChaseConfigLoader config, GangMembership gangs, DetainmentService detainment,
	                      WantedMessages messages, Supplier<Collection<? extends Player>> online,
	                      BiConsumer<Player, Integer> grantXp, ZoneId zone) {
	}

	public Result award(Player escaper, ChaseArc arc, WantedCause cause) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
