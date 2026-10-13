package org.luckyraven.gangland.copsncrooks.npc.police.perch;

import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.OverwatchSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Marksman overwatch (0.20.0): at 4+ stars, on loss of contact, sends the squad's Marksman to a roof overlooking the last
 * known spot; perched, he never fires, only reports the suspect when he sees him. Main thread only.
 *
 * @since 0.20.0
 */
@CustomLog
public class OverwatchController {

	/** A cop that has reached its perch is within this many blocks of it. */
	public static final double REACH_RADIUS = 1.5;
	/** {@code CopNpc.millisUnreachable()} at or above this means the walk is hopeless: next perch. */
	public static final long   UNREACHABLE_MILLIS = 5000L;

	private final Supplier<CopConfigProvider>                          provider;
	private final CopManager                                           copManager;
	private final CopRadio                                             copRadio;
	private final LongSupplier                                         clock;
	private final ToIntFunction<Player>                                levelOf;
	private final BiFunction<Location, OverwatchSettings, List<Location>> perches;

	public OverwatchController(Supplier<CopConfigProvider> provider, CopManager copManager, CopRadio copRadio,
	                           LongSupplier clock, ToIntFunction<Player> levelOf,
	                           BiFunction<Location, OverwatchSettings, List<Location>> perches) {
		this.provider   = provider;
		this.copManager = copManager;
		this.copRadio   = copRadio;
		this.clock      = clock;
		this.levelOf    = levelOf;
		this.perches    = perches;
	}

	/** The production wiring: perches come from {@link PerchFinder}. */
	public OverwatchController(Supplier<CopConfigProvider> provider, CopManager copManager, CopRadio copRadio,
	                           LongSupplier clock, ToIntFunction<Player> levelOf) {
		this(provider, copManager, copRadio, clock, levelOf, PerchFinder::find);
	}

	/** Sends the Marksman to a perch over {@code lastKnown}; a no-op when disabled, below Min_Level, active or spent. */
	public void start(Player player, Location lastKnown, int level) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Brings the Marksman down (releasing his post) into {@code postsTo} and clears the overwatch and its spent mark. */
	public void end(Player player, CopState postsTo) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Per AI tick: walk to the perch, hold it, report a sighting, enforce Max_Seconds and the star floor. */
	public void tick(Player player, @Nullable CopGroup group) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public boolean isActive(UUID playerId) {
		return false;
	}
}
