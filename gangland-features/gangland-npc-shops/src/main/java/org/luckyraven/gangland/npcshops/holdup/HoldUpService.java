package org.luckyraven.gangland.npcshops.holdup;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.npcshops.trader.mood.MoodService;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.util.Collection;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Polls (every 5 ticks, main thread) for players aiming a Bartizan weapon at a trader and runs the fill / alarm /
 * payout sessions. TDD skeleton: behaviour lands in the green wave. {@code plugin} may be null (no scheduling).
 */
public final class HoldUpService implements BeanLifecycle {

	private final JavaPlugin                             plugin;
	private final Supplier<HoldUpSettings>               loader;
	private final Supplier<Collection<? extends Player>> players;
	private final Predicate<Player>                      weaponHeld;
	private final BiFunction<Player, HoldUpSettings, HoldUpTarget> aimResolver;
	private final MoodService                            mood;
	private final CrimeService                           crimes;
	private final HoldUpPayout                           payout;
	private final LongSupplier                           clockMillis;
	private final BiConsumer<Player, String>             actionBar;
	private final Consumer<String>                       debug;

	public HoldUpService(JavaPlugin plugin, Supplier<HoldUpSettings> loader,
	                     Supplier<Collection<? extends Player>> players, Predicate<Player> weaponHeld,
	                     BiFunction<Player, HoldUpSettings, HoldUpTarget> aimResolver, MoodService mood, CrimeService crimes,
	                     HoldUpPayout payout, LongSupplier clockMillis, BiConsumer<Player, String> actionBar,
	                     Consumer<String> debug) {
		this.plugin      = plugin;
		this.loader      = loader;
		this.players     = players;
		this.weaponHeld  = weaponHeld;
		this.aimResolver = aimResolver;
		this.mood        = mood;
		this.crimes      = crimes;
		this.payout      = payout;
		this.clockMillis = clockMillis;
		this.actionBar   = actionBar;
		this.debug       = debug;
	}

	/** One poll. Called every 5 ticks by the repeating task. */
	public void tick() {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Re-reads the settings through the loader (managed reload). */
	public void reload() {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Drops every session and cooldown (shutdown / reload). */
	public void clear() {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void onPreClear() {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void onClear() {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void onShutdown() {
		throw new UnsupportedOperationException("TDD stub");
	}

	public boolean hasSession(UUID playerId) {
		return false;
	}

}
