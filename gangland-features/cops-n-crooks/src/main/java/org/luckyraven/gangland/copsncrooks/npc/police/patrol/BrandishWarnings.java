package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.npc.police.config.WarningSettings;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;

import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The "put that away" warning clock (0.20.0 lane patrol): per player, one warning per {@code Repeat_Seconds} counted
 * from when it was given; a warning ends complied (weapon away, or out of the cop's view when time is up) or ignored
 * (still holding, still in view) which commits {@code Brandish_Near_Cop} as seen by a cop. Main thread only.
 */
public class BrandishWarnings {

	/** How an open warning stands. */
	public enum Result {
		PENDING,
		COMPLIED,
		IGNORED
	}

	public BrandishWarnings(Supplier<WarningSettings> settings, LongSupplier clock, CrimeService crimes,
	                        Predicate<Player> holding, WantedMessages messages) {
	}

	/** Whether this player holds a weapon right now (the injected predicate). */
	public boolean holding(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Enabled, no open warning for this player and the repeat cooldown over. */
	public boolean canWarn(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Opens a warning (message {@code Patrol.Put_Away}, debug WARNING given); false when {@link #canWarn} is false. */
	public boolean begin(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/**
	 * Advances an open warning. {@code inView}: the warning cop still acquires the player. Weapon gone: COMPLIED
	 * (message {@code Patrol.Complied}). Time up and still holding: IGNORED when in view (commits the crime once),
	 * COMPLIED when not. Otherwise PENDING. A closed warning is dropped; the cooldown stays.
	 */
	public Result update(Player player, boolean inView) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Drops an open warning without a verdict (the cop died, left WARNING or the player left); cooldown stays. */
	public void abandon(UUID playerId) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
