package org.luckyraven.gangland.fights.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.arena.Arena;
import org.luckyraven.gangland.fights.arena.ArenaPoint;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.gangland.fights.fight.Fight;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.fights.fight.FightMessages;
import org.luckyraven.gangland.fights.fight.FightSide;
import org.luckyraven.gangland.fights.fight.FightState;
import org.luckyraven.gangland.fights.fight.Participant;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds fighters inside their arena box and everyone else outside it while a fight uses it (GP-31). Layers, in the
 * order the plan lists them: a move check, a teleport check (the fight's own teleports pass through
 * {@link ExpectedTeleports}), portals, and a five-tick sweep for what raises no event (elytra, knockback, another
 * plugin's {@code setLocation}). A fighter the sweep finds outside is put back at his side's spawn; after
 * {@code Guards.Max_Leave_Warnings} warnings the next leave forfeits. Blocks inside a busy arena are protected for
 * everyone when {@code Guards.Protect_Blocks} is on.
 */
@ListenerHandler
public final class FightContainmentListener implements Listener {

	private static final long SWEEP_TICKS = 5L;

	private final FightManager  manager;
	private final ArenaRegistry arenas;
	private final FightRules    rules;
	private final FightMessages messages;

	private final Map<UUID, Integer> warnings = new HashMap<>();

	/** @param plugin runs the sweep; null (tests) leaves it to the caller of {@link #check} */
	public FightContainmentListener(@Nullable JavaPlugin plugin, FightManager manager, ArenaRegistry arenas,
	                                FightRules rules, FightMessages messages) {
		this.manager  = manager;
		this.arenas   = arenas;
		this.rules    = rules;
		this.messages = messages;

		if (plugin != null) {
			Bukkit.getScheduler().runTaskTimer(plugin, () -> check(Bukkit.getOnlinePlayers()), SWEEP_TICKS, SWEEP_TICKS);
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onMove(PlayerMoveEvent event) {
		Location from = event.getFrom();
		Location to   = event.getTo();
		if (to == null || sameBlock(from, to)) return;

		Player player = event.getPlayer();
		Fight  fight  = Fighters.active(manager, player.getUniqueId());
		if (fight != null) {
			Arena arena = arenaOf(fight);
			if (arena != null && !inside(arena, to)) {
				event.setTo(from);
				Fighters.tellThrottled(player, messages, "Guards.Stay_In");
			}
			return;
		}

		Arena target = Fighters.busyArenaAt(manager, arenas, to);
		if (target != null && Fighters.busyArenaAt(manager, arenas, from) == null) {
			event.setTo(from);
			Fighters.tellThrottled(player, messages, "Guards.Arena_Busy", "arena", target.name());
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onTeleport(PlayerTeleportEvent event) {
		Player player = event.getPlayer();
		Location to   = event.getTo();
		if (to == null || ExpectedTeleports.expected(player.getUniqueId())) return;

		Fight fight = Fighters.active(manager, player.getUniqueId());
		if (fight != null) {
			// the result pause lets him go home; the countdown and the fight keep him in
			if (fight.state() == FightState.RESULT) return;

			Arena arena = arenaOf(fight);
			if (arena != null && !inside(arena, to)) {
				event.setCancelled(true);
				Fighters.tell(player, messages, "Guards.Teleport_Refused");
			}
			return;
		}

		Arena target = Fighters.busyArenaAt(manager, arenas, to);
		if (target != null) {
			event.setCancelled(true);
			Fighters.tell(player, messages, "Guards.Arena_Busy", "arena", target.name());
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPortal(PlayerPortalEvent event) {
		Player player = event.getPlayer();
		if (Fighters.active(manager, player.getUniqueId()) == null) return;

		event.setCancelled(true);
		Fighters.tell(player, messages, "Guards.Teleport_Refused");
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		if (protectedBlock(event.getBlock().getLocation())) {
			event.setCancelled(true);
			Fighters.tellThrottled(event.getPlayer(), messages, "Guards.Blocks_Protected");
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		if (protectedBlock(event.getBlock().getLocation())) {
			event.setCancelled(true);
			Fighters.tellThrottled(event.getPlayer(), messages, "Guards.Blocks_Protected");
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onVehicleEnter(VehicleEnterEvent event) {
		if (rules.isAllowVehicles() || !(event.getEntered() instanceof Player player)) return;
		if (Fighters.active(manager, player.getUniqueId()) == null) return;

		event.setCancelled(true);
		Fighters.tell(player, messages, "Guards.Vehicle_Refused");
	}

	/**
	 * The five-tick sweep over the online players: dismounts fighters, puts back (and warns, and finally forfeits)
	 * fighters found outside the box, and ejects outsiders found inside a busy arena.
	 */
	public void check(Collection<? extends Player> online) {
		for (Player player : online) {
			UUID  id    = player.getUniqueId();
			Fight fight = Fighters.active(manager, id);
			if (fight == null) {
				warnings.remove(id);
				ejectOutsider(player);
				continue;
			}
			if (fight.state() == FightState.RESULT) continue;

			if (!rules.isAllowVehicles() && player.isInsideVehicle()) {
				player.leaveVehicle();
			}
			Arena arena = arenaOf(fight);
			if (arena != null && !inside(arena, player.getLocation())) {
				leave(player, fight, arena);
			}
		}
	}

	private void leave(Player player, Fight fight, Arena arena) {
		UUID id    = player.getUniqueId();
		int  count = warnings.merge(id, 1, Integer::sum);
		int  max   = rules.getMaxLeaveWarnings();
		if (count > max) {
			warnings.remove(id);
			Fighters.tell(player, messages, "Guards.Left_Forfeit");
			manager.forfeit(id, "left");
			return;
		}

		Participant participant = fight.participant(id).orElse(null);
		if (participant != null) {
			List<ArenaPoint> spawns = participant.side() == FightSide.A ? arena.spawnsA() : arena.spawnsB();
			if (!spawns.isEmpty()) Fighters.teleport(player, spawns.get(0));
		}
		Fighters.tell(player, messages, "Guards.Leave_Warning", "count", count, "max", max);
	}

	private void ejectOutsider(Player player) {
		Arena arena = Fighters.busyArenaAt(manager, arenas, player.getLocation());
		if (arena == null) return;

		if (Fighters.teleport(player, arena.exit())) {
			Fighters.tell(player, messages, "Guards.Arena_Busy", "arena", arena.name());
		}
	}

	private boolean protectedBlock(Location at) {
		return rules.isProtectBlocks() && Fighters.busyArenaAt(manager, arenas, at) != null;
	}

	private @Nullable Arena arenaOf(Fight fight) {
		return fight.arenaName() == null ? null : arenas.byName(fight.arenaName()).orElse(null);
	}

	private static boolean inside(Arena arena, Location at) {
		return at.getWorld() != null && arena.box().contains(at.getWorld().getName(), at.getX(), at.getY(), at.getZ());
	}

	private static boolean sameBlock(Location a, Location b) {
		return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ()
		       && java.util.Objects.equals(a.getWorld(), b.getWorld());
	}
}
