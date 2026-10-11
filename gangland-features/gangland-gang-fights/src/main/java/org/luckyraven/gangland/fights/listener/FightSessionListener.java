package org.luckyraven.gangland.fights.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.fights.arena.Arena;
import org.luckyraven.gangland.fights.arena.ArenaPoint;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.gangland.fights.fight.Fight;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.fights.fight.FightMessages;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A fighter's connection to the server (GP-31, GP-41): quitting forfeits and restores him while he is still online,
 * joining inside an arena lands him at its exit (a crash or kick mid-fight strands nobody), and a death that skipped
 * the damage event (another plugin's {@code setHealth(0)}) keeps his inventory and level and drops nothing. The
 * lethal-hit guarantees themselves come from {@link FightDamageListener}; this is the net under it.
 */
@ListenerHandler
public final class FightSessionListener implements Listener {

	/** Arena staff may stand inside their arenas on join. */
	private static final String STAFF_NODE = "gangland.arena.admin";

	private final @Nullable JavaPlugin plugin;
	private final FightManager         manager;
	private final ArenaRegistry        arenas;
	private final FightMessages        messages;

	/** Fighters whose stray death was caught and who still have to respawn. */
	private final Set<UUID> fallen = new HashSet<>();

	/** @param plugin defers the forfeit of a respawning fighter by a tick; null (tests) forfeits at once */
	public FightSessionListener(@Nullable JavaPlugin plugin, FightManager manager, ArenaRegistry arenas,
	                            FightMessages messages) {
		this.plugin   = plugin;
		this.manager  = manager;
		this.arenas   = arenas;
		this.messages = messages;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		fallen.remove(id);
		manager.forfeit(id, "quit");
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (Fighters.active(manager, player.getUniqueId()) != null || player.hasPermission(STAFF_NODE)) return;

		Arena arena = arenas.at(player.getLocation()).orElse(null);
		if (arena != null && Fighters.teleport(player, arena.exit())) {
			Fighters.tell(player, messages, "Guards.Ejected", "arena", arena.name());
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onDeath(PlayerDeathEvent event) {
		UUID id = event.getEntity().getUniqueId();
		if (Fighters.active(manager, id) == null) return;

		event.setKeepInventory(true);
		event.setKeepLevel(true);
		event.getDrops().clear();
		event.setDroppedExp(0);
		fallen.add(id);
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onRespawn(PlayerRespawnEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		if (!fallen.remove(id)) return;

		Fight fight = manager.fightOf(id).orElse(null);
		Arena arena = fight == null || fight.arenaName() == null ? null : arenas.byName(fight.arenaName()).orElse(null);
		if (arena != null) {
			Location exit = location(arena.exit());
			if (exit != null) event.setRespawnLocation(exit);
		}

		if (plugin == null) {
			manager.forfeit(id, "death");
		} else {
			// the respawn is still in progress: let it finish before the fight moves him again
			Bukkit.getScheduler().runTask(plugin, () -> manager.forfeit(id, "death"));
		}
	}

	private static @Nullable Location location(ArenaPoint point) {
		World world = Bukkit.getWorld(point.world());
		return world == null ? null : new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
	}
}
