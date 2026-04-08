package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.command.sub.RespawnCommand;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.teleportation.IllegalTeleportException;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.gadget.jetpack.JetpackService;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TimeMessages;
import me.luckyraven.util.downed.DownedPlayerRegistry;
import me.luckyraven.util.downed.PlayerDownedEvent;
import me.luckyraven.util.downed.PlayerUndownedEvent;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.util.utilities.TimeUtil;
import net.citizensnpcs.api.CitizensAPI;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ListenerHandler
public class CustomPlayerDeath implements Listener {

	/**
	 * Minimum health set when a player is downed — keeps them alive server-side.
	 */
	private static final double DOWNED_HEALTH = 0.5;

	/**
	 * Static reference so {@link RespawnCommand} can reach this instance.
	 */
	private static CustomPlayerDeath instance;

	private final Gangland              gangland;
	private final UserManager<Player>   userManager;
	private final WaypointManager       waypointManager;
	private final JetpackService        jetpackService;
	private final Map<UUID, BukkitTask> respawnTasks   = new ConcurrentHashMap<>();
	private final Map<UUID, GameMode>   savedGameModes = new ConcurrentHashMap<>();

	public CustomPlayerDeath(Gangland gangland) {
		this.gangland = gangland;
		Initializer init = gangland.getInitializer();

		this.userManager     = init.getUserManager();
		this.waypointManager = init.getWaypointManager();
		this.jetpackService  = init.getJetpackService();
		instance             = this;
	}

	public static void triggerManualRespawn(Player player) {
		if (instance == null) return;
		instance.performRespawn(player);
	}

	/**
	 * Intercepts lethal damage before the player actually dies. When the hit would reduce health to or below the
	 * configured threshold, the damage is canceled and the player enters the downed state instead.
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityDamage(EntityDamageEvent event) {
		if (!Settings.isRespawnEnabled()) return;
		if (!(event.getEntity() instanceof Player player)) return;

		// Citizens NPCs with PLAYER entity type are instanceof Player — they must die normally.
		if (CitizensAPI.getNPCRegistry().isNPC(player)) return;

		UUID uuid = player.getUniqueId();

		// Already downed — absorb all further damage until they respawn.
		if (DownedPlayerRegistry.isDowned(uuid)) {
			event.setCancelled(true);
			return;
		}

		double resultHealth = player.getHealth() - event.getFinalDamage();
		if (resultHealth <= 0) {
			event.setCancelled(true);
			player.setHealth(DOWNED_HEALTH);
			// Mark as downed immediately so any damage in the 1-tick scheduler delay is absorbed.
			DownedPlayerRegistry.add(uuid);
			// Delay 1 tick so the cancelled damage is fully processed first.
			Bukkit.getScheduler().runTaskLater(gangland, () -> enterDownedState(player), 1L);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		cleanup(event.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (DownedPlayerRegistry.isDowned(event.getPlayer().getUniqueId())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		if (DownedPlayerRegistry.isDowned(event.getPlayer().getUniqueId())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		if (DownedPlayerRegistry.isDowned(event.getPlayer().getUniqueId())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onItemDrop(PlayerDropItemEvent event) {
		if (DownedPlayerRegistry.isDowned(event.getPlayer().getUniqueId())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onItemConsume(PlayerItemConsumeEvent event) {
		if (DownedPlayerRegistry.isDowned(event.getPlayer().getUniqueId())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onItemPickup(EntityPickupItemEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		if (DownedPlayerRegistry.isDowned(player.getUniqueId())) {
			event.setCancelled(true);
		}
	}

	private void enterDownedState(Player player) {
		if (!player.isOnline()) return;

		if (jetpackService != null) jetpackService.deactivate(player);

		UUID uuid = player.getUniqueId();
		DownedPlayerRegistry.add(uuid);
		savedGameModes.put(uuid, player.getGameMode());

		Bukkit.getPluginManager().callEvent(new PlayerDownedEvent(player));

		GameMode gm = parseGameMode(Settings.getRespawnGameMode());
		player.setGameMode(gm);

		if (Settings.isRespawnGameModeAllowFly()) {
			player.setAllowFlight(true);
			player.setFlying(true);
		}

		int delay = Settings.getRespawnDelay();

		if (delay <= 0) {
			showTitle(player, 0);
			performRespawn(player);
			return;
		}

		showTitle(player, delay);
		sendRespawnButton(player);
		startCountdown(player, delay);
	}

	private void startCountdown(Player player, int delay) {
		UUID uuid = player.getUniqueId();

		BukkitRunnable runnable = new BukkitRunnable() {
			int remaining = delay;

			@Override
			public void run() {
				if (!player.isOnline()) {
					cleanup(uuid);
					this.cancel();
					return;
				}

				remaining--;

				if (remaining <= 0) {
					respawnTasks.remove(uuid);
					this.cancel();
					performRespawn(player);
					return;
				}

				showTitle(player, remaining);
			}
		};

		respawnTasks.put(uuid, runnable.runTaskTimer(gangland, 20L, 20L));
	}

	private void performRespawn(Player player) {
		if (!player.isOnline()) return;

		UUID uuid = player.getUniqueId();

		BukkitTask task = respawnTasks.remove(uuid);
		if (task != null) task.cancel();

		DownedPlayerRegistry.remove(uuid);

		double maxHealth = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
		double amount    = Math.min(Settings.getRespawnHealthAmount(), maxHealth);

		player.setHealth(amount);
		player.setFoodLevel(Settings.getRespawnHungerAmount());

		GameMode original = savedGameModes.remove(uuid);
		player.setGameMode(original != null ? original : GameMode.SURVIVAL);
		player.setAllowFlight(false);
		player.setFlying(false);

		if (Settings.isRespawnTeleportEnabled()) {
			User<Player> user     = userManager.getUser(player);
			Waypoint     waypoint = waypointManager.get(Settings.getRespawnTeleportWaypoint());
			if (user != null && waypoint != null) {
				try {
					waypoint.getWaypointTeleport().teleport(gangland, user, (u, t) -> { });
				} catch (IllegalTeleportException ignored) {
					// player was on waypoint cooldown — teleport directly
					Location loc = waypoint.getLocation();
					if (loc != null) player.teleport(loc);
				}
			}
		}

		player.sendTitle("", "", 0, 1, 1);

		// Vanilla PlayerRespawnEvent does not fire here (player never actually died), so notify listeners
		// that the player is back on their feet via the custom counterpart.
		Bukkit.getPluginManager().callEvent(new PlayerUndownedEvent(player));
	}

	private void cleanup(UUID uuid) {
		BukkitTask task = respawnTasks.remove(uuid);
		if (task != null) task.cancel();
		DownedPlayerRegistry.remove(uuid);
		savedGameModes.remove(uuid);
	}

	private void sendRespawnButton(Player player) {
		TextComponent prefix = new TextComponent(GanglandChatUtil.color("&8[&cWASTED&8] "));
		TextComponent button = new TextComponent(GanglandChatUtil.color("&7[&aClick to respawn instantly&7]"));
		button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + Gangland.SHORT_PREFIX + " respawn"));
		prefix.addExtra(button);
		player.spigot().sendMessage(prefix);
	}

	private void showTitle(Player player, int seconds) {
		if (!Settings.isRespawnScreenEnabled()) return;

		String title = ChatUtil.color(Settings.getRespawnScreenTitle());

		String formatTime = TimeUtil.formatTime(seconds, true, TimeMessages.getInstance());
		String replace    = Settings.getRespawnScreenSubtitle().replace("%time%", formatTime);
		String subtitle   = ChatUtil.color(replace);

		// fadeIn=0, stay=25 ticks (~1.25 s), fadeOut=5 — replaced each second by the timer.
		player.sendTitle(title, subtitle, 0, 25, 5);
	}

	private GameMode parseGameMode(String name) {
		try {
			return GameMode.valueOf(name.toUpperCase());
		} catch (IllegalArgumentException ignored) {
			return GameMode.SURVIVAL;
		}
	}

}
