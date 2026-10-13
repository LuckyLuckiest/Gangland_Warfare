package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PullOverSettings;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.util.ArrayList;
import java.util.HashMap;
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
@CustomLog
public class PullOverTracker implements BeanLifecycle {

	/** A CarMoveEvent report older than this counts as the car having stopped. */
	private static final long SPEED_STALE_MS = 500L;

	private static final class Order {

		final long start;
		double speed;
		long   speedAt;
		long   holdStart = -1;

		Order(long start, double speed) {
			this.start   = start;
			this.speed   = speed;
			this.speedAt = start;
		}
	}

	private final JavaPlugin                          plugin;
	private final Supplier<PullOverSettings>          settings;
	private final LongSupplier                        clock;
	private final CrimeService                        crimes;
	private final DetainmentEconomyContract           economy;
	private final WantedMessages                      messages;
	private final Function<UUID, @Nullable Player>    players;
	private final DoubleFunction<Map<String, String>> money;
	private final Map<UUID, Order>                    orders      = new HashMap<>();
	private final Map<UUID, Long>                     lastOrdered = new HashMap<>();
	private       BukkitTask                          task;

	/**
	 * @param players resolves an online player by id ({@code null} when gone)
	 * @param money the {@code %money_symbol%}/{@code %amount%} placeholders for an amount
	 */
	public PullOverTracker(JavaPlugin plugin, Supplier<PullOverSettings> settings, LongSupplier clock, CrimeService crimes,
	                       DetainmentEconomyContract economy, WantedMessages messages,
	                       Function<UUID, @Nullable Player> players, DoubleFunction<Map<String, String>> money) {
		this.plugin   = plugin;
		this.settings = settings;
		this.clock    = clock;
		this.crimes   = crimes;
		this.economy  = economy;
		this.messages = messages;
		this.players  = players;
		this.money    = money;
	}

	/** Enabled, no open order and the repeat cooldown (counted from the order) over. */
	public boolean canOrder(UUID playerId) {
		PullOverSettings s    = settings.get();
		Long             last = lastOrdered.get(playerId);
		return s.enabled() && !orders.containsKey(playerId) &&
		       (last == null || clock.getAsLong() - last >= s.repeatSeconds() * 1000L);
	}

	public boolean isOrdered(UUID playerId) {
		return orders.containsKey(playerId);
	}

	/** Opens the order (message {@code Patrol.Pull_Over}, debug order); false when {@link #canOrder} is false. */
	public boolean order(Player player, double speed) {
		UUID id = player.getUniqueId();
		if (!canOrder(id)) {
			return false;
		}
		long now = clock.getAsLong();
		orders.put(id, new Order(now, speed));
		lastOrdered.put(id, now);
		player.sendMessage(messages.format(WantedMessages.Key.PATROL_PULL_OVER,
		                                   Map.of("seconds", String.valueOf(settings.get().complySeconds()))));
		log.debug("PULLOVER {} speed={} result=order amount=0", player.getName(), speed);
		return true;
	}

	/** The latest CarMoveEvent speed of a driver, in blocks per second. */
	public void onSpeed(UUID playerId, double blocksPerSecond) {
		Order order = orders.get(playerId);
		if (order != null) {
			order.speed   = blocksPerSecond;
			order.speedAt = clock.getAsLong();
		}
	}

	/** Advances every open order once (called about once a second); an order whose player is offline is dropped. */
	public void tick() {
		if (orders.isEmpty()) {
			return;
		}
		PullOverSettings s = settings.get();
		for (Map.Entry<UUID, Order> entry : new ArrayList<>(orders.entrySet())) {
			UUID   id     = entry.getKey();
			Order  order  = entry.getValue();
			Player player = players.apply(id);
			if (player == null || !player.isOnline()) {
				orders.remove(id);
				continue;
			}
			long   now   = clock.getAsLong();
			double speed = now - order.speedAt > SPEED_STALE_MS ? 0.0 : order.speed;
			if (speed <= s.stopSpeed()) {
				if (order.holdStart < 0) {
					order.holdStart = now;
				}
			} else {
				order.holdStart = -1;
			}

			if (order.holdStart >= 0 && now - order.holdStart >= s.stopHoldSeconds() * 1000L) {
				double balance = economy.getBalance(player);
				double amount  = Math.floor(Math.min(s.ticketAmount(), balance) * 100) / 100;
				double charged = 0;
				if (amount > 0 &&
				    economy.tryCharge(player, amount) == DetainmentEconomyContract.ChargeResult.SUCCESS) {
					charged = amount;
				}
				player.sendMessage(messages.format(WantedMessages.Key.PATROL_TICKET, money.apply(charged)));
				orders.remove(id);
				log.debug("PULLOVER {} speed={} result=ticket amount={}", player.getName(), speed, charged);
			} else if (now - order.start >= s.complySeconds() * 1000L) {
				crimes.commit(player, Crimes.EVADING_POLICE, player.getLocation(), true, 0);
				player.sendMessage(messages.format(WantedMessages.Key.PATROL_FLED, Map.of()));
				orders.remove(id);
				log.debug("PULLOVER {} speed={} result=fled amount=0", player.getName(), speed);
			}
		}
	}

	/** Starts the repeating main-thread task that calls {@link #tick()} every 10 ticks. */
	@Override
	public void onInitialize(boolean firstLoad) {
		if (task != null) {
			task.cancel();
		}
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
	}

	/** Stops the task and drops every order. */
	@Override
	public void onShutdown() {
		if (task != null) {
			task.cancel();
			task = null;
		}
		orders.clear();
		lastOrdered.clear();
	}

	/** Forgets the order and the cooldown of a player who quit. */
	public void forget(UUID playerId) {
		orders.remove(playerId);
		lastOrdered.remove(playerId);
	}
}
