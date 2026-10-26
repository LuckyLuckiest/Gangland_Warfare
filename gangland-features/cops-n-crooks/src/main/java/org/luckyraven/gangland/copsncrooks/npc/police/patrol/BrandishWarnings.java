package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.npc.police.config.WarningSettings;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The "put that away" warning clock (0.20.0 lane patrol): per player, one warning per {@code Repeat_Seconds} counted
 * from the verdict of the last one (0.21.1; it used to count from the start of the warning); a warning ends complied
 * (weapon away, or out of the cop's view when time is up, which says so) or ignored (still holding, still in view): the
 * player is told the cause and {@code Brandish_Near_Cop} is committed as seen by a cop, which is worth the first star on
 * the shipped heat. Main thread only.
 */
@CustomLog
public class BrandishWarnings {

	/** How an open warning stands. */
	public enum Result {
		PENDING,
		COMPLIED,
		IGNORED
	}

	private final Supplier<WarningSettings> settings;
	private final LongSupplier              clock;
	private final CrimeService              crimes;
	private final Predicate<Player>         holding;
	private final WantedMessages            messages;
	private final Map<UUID, Long>           open      = new HashMap<>();
	private final Map<UUID, Long>           lastGiven = new HashMap<>();

	public BrandishWarnings(Supplier<WarningSettings> settings, LongSupplier clock, CrimeService crimes,
	                        Predicate<Player> holding, WantedMessages messages) {
		this.settings = settings;
		this.clock    = clock;
		this.crimes   = crimes;
		this.holding  = holding;
		this.messages = messages;
	}

	/** Whether this player holds a weapon right now (the injected predicate). */
	public boolean holding(Player player) {
		return holding.test(player);
	}

	/** Enabled, no open warning for this player and the repeat cooldown over. */
	public boolean canWarn(Player player) {
		WarningSettings s  = settings.get();
		UUID             id = player.getUniqueId();
		if (!s.enabled() || open.containsKey(id)) {
			return false;
		}
		Long last = lastGiven.get(id);
		return last == null || clock.getAsLong() - last >= s.repeatSeconds() * 1000L;
	}

	/** Opens a warning (message {@code Patrol.Put_Away}, debug WARNING given); false when {@link #canWarn} is false. */
	public boolean begin(Player player) {
		if (!canWarn(player)) {
			return false;
		}
		UUID id  = player.getUniqueId();
		long now = clock.getAsLong();
		open.put(id, now);
		player.sendMessage(messages.format(WantedMessages.Key.PATROL_PUT_AWAY,
		                                   Map.of("seconds", String.valueOf(settings.get().seconds()))));
		log.debug("WARNING {} result=given", player.getName());
		return true;
	}

	/**
	 * Advances an open warning. {@code inView}: the warning cop still acquires the player. Weapon gone: COMPLIED
	 * (message {@code Patrol.Complied}). Time up and still holding: IGNORED when in view (commits the crime once),
	 * COMPLIED when not. Otherwise PENDING. A closed warning is dropped and starts the repeat cooldown.
	 */
	public Result update(Player player, boolean inView) {
		UUID id      = player.getUniqueId();
		Long started = open.get(id);
		if (started == null) {
			return Result.COMPLIED;
		}
		if (!holding(player)) {
			close(id);
			player.sendMessage(messages.format(WantedMessages.Key.PATROL_COMPLIED, Map.of()));
			log.debug("WARNING {} result=complied", player.getName());
			return Result.COMPLIED;
		}
		if (clock.getAsLong() - started < settings.get().seconds() * 1000L) {
			return Result.PENDING;
		}
		close(id);
		if (!inView) {
			player.sendMessage(messages.format(WantedMessages.Key.PATROL_LOST_SIGHT, Map.of()));
			log.debug("WARNING {} result=complied", player.getName());
			return Result.COMPLIED;
		}
		player.sendMessage(messages.format(WantedMessages.Key.PATROL_IGNORED,
		                                   Map.of("cause", messages.crimeName(Crimes.BRANDISH_NEAR_COP))));
		crimes.commit(player, Crimes.BRANDISH_NEAR_COP, player.getLocation(), true, 0);
		log.debug("WARNING {} result=ignored", player.getName());
		return Result.IGNORED;
	}

	/** Drops an open warning without a verdict (the cop died, left WARNING or the player left); the cooldown starts. */
	public void abandon(UUID playerId) {
		close(playerId);
	}

	/** Closes the open warning, if any, and stamps the cooldown now: the verdict, not the start, begins it. */
	private void close(UUID playerId) {
		if (open.remove(playerId) != null) {
			lastGiven.put(playerId, clock.getAsLong());
		}
	}
}
