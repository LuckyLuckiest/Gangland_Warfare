package org.luckyraven.gangland.gadget.jetpack;

import io.netty.channel.Channel;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.item.fuel.FuelKey;
import org.luckyraven.gangland.item.fuel.FuelService;
import org.luckyraven.gangland.gadget.jetpack.packet.JetpackInputInterceptor;
import org.luckyraven.keystone.nms.input.PlayerInputInterceptor;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active jetpack sessions. Handles activation, deactivation, and lifecycle of jetpack flight for all players.
 */
public class JetpackService implements BeanLifecycle {

	private static final String LEGACY_WEARABLE_TAG = "wearable";

	private final Map<UUID, JetpackSession> activeSessions = new ConcurrentHashMap<>();
	private final FuelService               fuelService;
	private final JavaPlugin                plugin;
	private final GadgetPhysicsConfig       physicsConfig;
	private final JetpackAddon              jetpackAddon;

	public JetpackService(FuelService fuelService, JavaPlugin plugin, GadgetPhysicsConfig physicsConfig,
	                      JetpackAddon jetpackAddon) {
		this.fuelService   = fuelService;
		this.plugin        = plugin;
		this.physicsConfig = physicsConfig;
		this.jetpackAddon  = jetpackAddon;
	}

	/**
	 * Activates the jetpack for the given player. Creates a session and starts the tick task.
	 */
	public void activate(Player player, Jetpack jetpack) {
		if (activeSessions.containsKey(player.getUniqueId())) return;

		// Silent (review I3): this is reached by every scheduleChestplateCheck caller, including join
		// (JetpackActivateListener), dismount/undown (JetpackSessionLifecycleListener) and any chestplate-shaped
		// interaction — a denial here must not spam the player on every one of those. The message fires only from
		// JetpackEquipListener's two deliberate-interaction paths (Car precedent: CarInteractListener.java:59).
		if (!player.hasPermission(jetpack.getPermission())) return;

		JetpackSession session = new JetpackSession(player, jetpack);
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
			migrateLegacyJetpack(player);

			ItemStack chestplate = player.getInventory().getChestplate();
			String    id         = chestplate != null ? Jetpack.getJetpackId(chestplate) : null;
			Jetpack   jetpack    = id != null ? jetpackAddon.getJetpack(id) : null;

			if (jetpack != null) {
				activate(player, jetpack);
				return;
			}

			if (!isActive(player)) return;
			deactivate(player);
		});
	}

	/**
	 * Migrates a pre-0.9.2 jetpack still carrying Bartizan's old identity tag ({@link #LEGACY_WEARABLE_TAG}, the
	 * raw string value of Bartizan's {@code Wearable.NBT_KEY} — no Bartizan import needed, it's a plain NBT string
	 * key) by stamping the new {@link JetpackKey#JETPACK_ID} tag in place, preserving every existing tag including
	 * the fuel level — deliberately NOT the {@code ItemRefresherRegistry} path, which would reset fuel/durability to
	 * factory defaults (wrong for a live migration). A no-op once the item already carries a jetpack tag, or if it
	 * never had the legacy tag, or if the legacy catalogue key names a jetpack that no longer exists in
	 * items/jetpacks.yml.
	 */
	private void migrateLegacyJetpack(Player player) {
		ItemStack chestplate = player.getInventory().getChestplate();
		if (chestplate == null || chestplate.getType().isAir()) return;
		if (Jetpack.isJetpackItem(chestplate)) return;

		ItemBuilder builder = new ItemBuilder(chestplate);
		if (!builder.hasNBTTag(LEGACY_WEARABLE_TAG) || !builder.hasNBTTag(FuelKey.FUEL_ID.getKey())) return;

		String legacyId = builder.getStringTagData(LEGACY_WEARABLE_TAG);
		if (legacyId == null || jetpackAddon.getJetpack(legacyId) == null) return;

		builder.addTag(JetpackKey.JETPACK_ID.getKey(), legacyId);
		player.getInventory().setChestplate(builder.build());
	}

	/**
	 * Refreshes the {@link Jetpack} definition references held by all active jetpack sessions. Must be called after
	 * the jetpack addon has been reloaded from config (e.g. on {@code /glw reload}) so that existing sessions
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
			ItemStack chestplate = player.getInventory().getChestplate();
			String    id         = chestplate != null ? Jetpack.getJetpackId(chestplate) : null;
			Jetpack   fresh      = id != null ? jetpackAddon.getJetpack(id) : null;
			if (fresh != null) {
				session.setJetpack(fresh);
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
