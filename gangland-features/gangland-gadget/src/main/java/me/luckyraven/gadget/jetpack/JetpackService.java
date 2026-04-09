package me.luckyraven.gadget.jetpack;

import io.netty.channel.Channel;
import me.luckyraven.gadget.car.vehicle.packet.VehicleInputInterceptor;
import me.luckyraven.gadget.config.GadgetPhysicsConfig;
import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.gadget.jetpack.packet.JetpackInputInterceptor;
import me.luckyraven.item.wearable.Wearable;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import me.luckyraven.weapon.wearable.WearableService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active jetpack sessions. Handles activation, deactivation, and lifecycle of jetpack flight for all players.
 */
public class JetpackService implements BeanLifecycle {

	private final Map<UUID, JetpackSession> activeSessions = new ConcurrentHashMap<>();
	private final FuelService               fuelService;
	private final JavaPlugin                plugin;
	private final GadgetPhysicsConfig       physicsConfig;
	private final WearableService           wearableService;

	public JetpackService(FuelService fuelService, JavaPlugin plugin, GadgetPhysicsConfig physicsConfig,
	                      WearableService wearableService) {
		this.fuelService     = fuelService;
		this.plugin          = plugin;
		this.physicsConfig   = physicsConfig;
		this.wearableService = wearableService;
	}

	/**
	 * Activates the jetpack for the given player. Creates a session and starts the tick task.
	 */
	public void activate(Player player, Wearable jetpackWearable) {
		if (activeSessions.containsKey(player.getUniqueId())) return;

		JetpackSession session = new JetpackSession(player, jetpackWearable);
		JetpackTask    task    = new JetpackTask(session, this, fuelService, physicsConfig);
		session.setTask(task);

		activeSessions.put(player.getUniqueId(), session);
		task.runTaskTimer(plugin, 1L, 1L);

		// Suppress Spigot's anti-cheat kick ("Flying is not enabled for this server").
		// We do NOT call setFlying(true) — flight is driven entirely by velocity in JetpackTask.
		player.setAllowFlight(true);

		Channel channel = VehicleInputInterceptor.getChannel(player);
		if (channel != null) {
			channel.pipeline()
			       .addBefore("packet_handler", JetpackInputInterceptor.HANDLER_NAME,
			                  new JetpackInputInterceptor(session));
		}
	}

	/**
	 * Deactivates the jetpack for the given player. Cancels the task and cleans up flight state.
	 */
	public void deactivate(Player player) {
		JetpackSession session = activeSessions.remove(player.getUniqueId());
		if (session == null) return;

		if (session.getTask() != null && !session.getTask().isCancelled()) {
			session.getTask().cancel();
		}

		if (player.isOnline()) {
			player.setFlying(false);
			player.setAllowFlight(false);

			Channel channel = VehicleInputInterceptor.getChannel(player);
			if (channel != null && channel.pipeline().get(JetpackInputInterceptor.HANDLER_NAME) != null) {
				channel.pipeline().remove(JetpackInputInterceptor.HANDLER_NAME);
			}
		}
	}

	/**
	 * Returns whether the given player has an active jetpack session.
	 */
	public boolean isActive(Player player) {
		return activeSessions.containsKey(player.getUniqueId());
	}

	/**
	 * Returns the active jetpack session for the player, or {@code null}.
	 */
	@Nullable
	public JetpackSession getSession(Player player) {
		return activeSessions.get(player.getUniqueId());
	}

	/**
	 * Schedules a next-tick check of the player's chestplate slot and activates or deactivates the jetpack
	 * accordingly.
	 */
	public void scheduleChestplateCheck(Player player) {
		player.getServer().getScheduler().runTask(plugin, () -> {
			ItemStack chestplate = player.getInventory().getChestplate();
			Wearable  wearable   = chestplate != null ? wearableService.resolveWearable(chestplate) : null;

			if (wearable != null && wearable.isJetpack()) {
				activate(player, wearable);
				return;
			}

			if (!isActive(player)) return;
			deactivate(player);
		});
	}

	/**
	 * Refreshes the {@link Wearable} definition references held by all active jetpack sessions. Must be called after
	 * the wearable addon has been reloaded from config (e.g. on {@code /glw reload}) so that existing sessions
	 * immediately pick up updated physics values. Sessions whose player is offline or no longer wearing a jetpack are
	 * deactivated.
	 */
	public void refreshSessions() {
		for (JetpackSession session : new ArrayList<>(activeSessions.values())) {
			Player player = session.getPlayer();
			if (!player.isOnline()) {
				deactivate(player);
				continue;
			}
			ItemStack chestplate    = player.getInventory().getChestplate();
			Wearable  freshWearable = chestplate != null ? wearableService.resolveWearable(chestplate) : null;
			if (freshWearable != null && freshWearable.isJetpack()) {
				session.setJetpackWearable(freshWearable);
			} else {
				deactivate(player);
			}
		}
	}

	/**
	 * Deactivates all active jetpack sessions. Called on plugin disable.
	 */
	public void deactivateAll() {
		for (JetpackSession session : activeSessions.values()) {
			if (session.getTask() != null && !session.getTask().isCancelled()) {
				session.getTask().cancel();
			}
			Player player = session.getPlayer();
			if (player.isOnline()) {
				player.setFlying(false);
				player.setAllowFlight(false);

				Channel channel = VehicleInputInterceptor.getChannel(player);
				if (channel != null && channel.pipeline().get(JetpackInputInterceptor.HANDLER_NAME) != null) {
					channel.pipeline().remove(JetpackInputInterceptor.HANDLER_NAME);
				}
			}
		}
		activeSessions.clear();
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (!firstLoad) refreshSessions();
	}

	@Override
	public void onShutdown() {
		deactivateAll();
	}

}
