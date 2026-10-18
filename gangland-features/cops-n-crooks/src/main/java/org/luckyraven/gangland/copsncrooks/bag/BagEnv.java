package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;

/**
 * Everything {@link BagService} needs from the server, as one seam: a fake in tests, {@code BagModuleConfig} in
 * production.
 *
 * @since 0.20.0
 */
public interface BagEnv {

	/** The player's current wanted level, 0 when unknown. */
	int wantedLevel(Player player);

	/** {@code DetainmentService.isRestrained}. */
	boolean restrained(Player player);

	/** Adds to the online user's wallet. */
	void deposit(Player player, BigDecimal amount);

	/** Drops {@code amount} as hot bills at {@code at} (main thread). */
	void spill(Player player, Location at, BigDecimal amount);

	/** Server-local {@code LocalDate.now().toEpochDay()}. */
	long epochDay();

	long nowMillis();

	/** Sends {@code Bag.Hud} for the current bag. */
	void hud(Player player, BigDecimal bag);

	void debug(String line);

	/** Runs on the next main-thread tick, queued behind earlier {@code runTask} calls. */
	void runTask(Runnable task);

	/** {@code WantedStars.raise(user, 1, CRIME)}. */
	void raiseHotStar(Player player);

	/** A money item carrying the hot tag. */
	boolean isHotMoney(ItemStack item);
}
