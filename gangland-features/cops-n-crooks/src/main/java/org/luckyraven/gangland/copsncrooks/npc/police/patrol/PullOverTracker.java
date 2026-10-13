package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PullOverSettings;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Per-player stop orders (0.20.0 lane patrol): an ordered driver who holds below {@code Stop_Speed} for
 * {@code Stop_Hold_Seconds} within {@code Comply_Seconds} is ticketed from the wallet (what the wallet holds if
 * less); one who does not commits {@code Evading_Police} as seen by a cop. One order per player per
 * {@code Repeat_Seconds}. Main thread only.
 */
public class PullOverTracker implements BeanLifecycle {

	/**
	 * @param players resolves an online player by id ({@code null} when gone)
	 * @param money the {@code %money_symbol%}/{@code %amount%} placeholders for an amount
	 */
	public PullOverTracker(JavaPlugin plugin, Supplier<PullOverSettings> settings, LongSupplier clock, CrimeService crimes,
	                       DetainmentEconomyContract economy, WantedMessages messages,
	                       Function<UUID, @Nullable Player> players, DoubleFunction<Map<String, String>> money) {
	}

	/** Enabled, no open order and the repeat cooldown (counted from the order) over. */
	public boolean canOrder(UUID playerId) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public boolean isOrdered(UUID playerId) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Opens the order (message {@code Patrol.Pull_Over}, debug order); false when {@link #canOrder} is false. */
	public boolean order(Player player, double speed) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The latest CarMoveEvent speed of a driver, in blocks per second. */
	public void onSpeed(UUID playerId, double blocksPerSecond) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Advances every open order once (called about once a second); an order whose player is offline is dropped. */
	public void tick() {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Starts the repeating main-thread task that calls {@link #tick()} every 10 ticks. */
	@Override
	public void onInitialize(boolean firstLoad) {
	}

	/** Stops the task and drops every order. */
	@Override
	public void onShutdown() {
	}

	/** Forgets the order and the cooldown of a player who quit. */
	public void forget(UUID playerId) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
