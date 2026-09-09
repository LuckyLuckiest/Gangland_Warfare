package org.luckyraven.gangland.gadget.jetpack;

import io.netty.channel.Channel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.item.fuel.FuelService;
import org.luckyraven.gangland.gadget.jetpack.packet.JetpackInputInterceptor;
import org.luckyraven.keystone.nms.input.PlayerInputInterceptor;
import org.luckyraven.bartizan.api.BartizanApi;
import org.luckyraven.bartizan.api.wearable.Wearable;
import org.luckyraven.bartizan.api.wearable.WearableCatalog;

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

	public JetpackService(FuelService fuelService, JavaPlugin plugin, GadgetPhysicsConfig physicsConfig) {
		this.fuelService   = fuelService;
		this.plugin        = plugin;
		this.physicsConfig = physicsConfig;
	}

	/**
	 * Resolves Bartizan's {@link WearableCatalog} lazily, never cached in a field — Bartizan may enable after this
	 * module (or not be installed at all, per {@code module.yml}'s {@code Plugins: [Bartizan]}).
	 */
	@Nullable
	private WearableCatalog wearables() {
		RegisteredServiceProvider<BartizanApi> rsp = Bukkit.getServicesManager().getRegistration(BartizanApi.class);
		return rsp == null ? null : rsp.getProvider().wearables();
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

		Channel channel = PlayerInputInterceptor.getChannel(player);
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

			Channel channel = PlayerInputInterceptor.getChannel(player);
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
			WearableCatalog wearables  = wearables();
			ItemStack       chestplate = player.getInventory().getChestplate();
			Wearable wearable = chestplate != null && wearables != null ?
			                   wearables.resolveWearable(chestplate) :
			                   null;

			if (wearable != null && isJetpack(wearable)) {
				activate(player, wearable);
				return;
			}

			if (!isActive(player)) return;
			deactivate(player);
		});
	}

	/**
	 * A wearable is a jetpack when it carries the {@code fuel} extra tag (P2 Q2 — jetpack data has no dedicated
	 * {@code Wearable} fields any more, replacing the deleted {@code Wearable#isJetpack()}).
	 *
	 * <p>Public so {@code GadgetModuleConfig} can reuse the same predicate for
	 * {@link org.luckyraven.gangland.item.fuel.FuelService#setFuelSinkPredicate} (T-KR2, review B2) instead of
	 * duplicating the "fuel" tag check.
	 */
	public static boolean isJetpack(Wearable wearable) {
		return wearable.extraTags().containsKey("fuel");
	}

	/**
	 * Refreshes the {@link Wearable} definition references held by all active jetpack sessions. Must be called after
	 * the wearable addon has been reloaded from config (e.g. on {@code /glw reload}) so that existing sessions
	 * immediately pick up updated physics values. Sessions whose player is offline or no longer wearing a jetpack are
	 * deactivated.
	 */
	public void refreshSessions() {
		WearableCatalog wearables = wearables();
		for (JetpackSession session : new ArrayList<>(activeSessions.values())) {
			Player player = session.getPlayer();
			if (!player.isOnline()) {
				deactivate(player);
				continue;
			}
			ItemStack chestplate = player.getInventory().getChestplate();
			Wearable  freshWearable = chestplate != null && wearables != null ?
			                         wearables.resolveWearable(chestplate) :
			                         null;
			if (freshWearable != null && isJetpack(freshWearable)) {
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

				Channel channel = PlayerInputInterceptor.getChannel(player);
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
