package org.luckyraven.gangland.turf.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.events.teleportation.TeleportEvent;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.TurfRelation;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * W5-T (PLAN 15.3 C2): a gang teleport ({@link Waypoint.WaypointType#GANG} only) is cancelled when it would leave a turf
 * under attack, land in or within {@code Teleport_Guard.Buffer_Blocks} of one, land in a rival turf, or when the
 * player's gang is owner or challenger in any running contest. Reads {@link TurfManager} runtime state only.
 */
@ListenerHandler
public final class TurfTeleportGuardListener implements Listener {

	private final TurfManager        turfs;
	private final GangLookupContract gangs;
	private final TurfRules          rules;
	private final TurfMessages       text;

	public TurfTeleportGuardListener(TurfManager turfs, GangLookupContract gangs, TurfRules rules, TurfMessages text) {
		this.turfs = turfs;
		this.gangs = gangs;
		this.rules = rules;
		this.text  = text;
	}

	/** true when a contest is running on {@code turf}. */
	static boolean underAttack(TurfManager turfs, Turf turf) {
		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		return state != null && state.getState() == TurfState.CONTESTING;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onTeleport(TeleportEvent event) {
		Waypoint     waypoint = event.getWaypoint();
		User<Player> user     = event.getUser();
		if (waypoint == null || waypoint.getType() != Waypoint.WaypointType.GANG || user == null || !user.hasGang()) {
			return;
		}
		Player   player      = user.getUser();
		int      gangId      = user.getGangId();
		Location destination = waypoint.getLocation();

		Turf source = turfs.findAt(event.getFrom());
		if (source != null && underAttack(turfs, source)) {
			refuse(event, player, "Source_Under_Attack",
			       "&cYou cannot leave &f%turf% &cby teleport while it is under attack.", source);
			return;
		}

		if (destination != null && destination.getWorld() != null) {
			int buffer = rules.getTeleportGuardBufferBlocks();
			for (Turf turf : turfs.getTurfsInWorld(destination.getWorld().getName())) {
				if (underAttack(turfs, turf) && withinBuffer(turf.getRegion(), destination, buffer)) {
					refuse(event, player, "Destination_Under_Attack",
					       "&cYou cannot teleport into or next to &f%turf% &cwhile it is under attack.", turf);
					return;
				}
			}
			Turf target = turfs.findAt(destination);
			if (target != null && TurfRelation.of(target, gangId, gangs) == TurfRelation.RIVAL) {
				refuse(event, player, "Destination_Rival",
				       "&cYou cannot teleport into &f%turf%&c: a rival gang holds it.", target);
				return;
			}
		}

		for (Turf turf : turfs.getAll()) {
			TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
			if (state == null || state.getState() != TurfState.CONTESTING) {
				continue;
			}
			boolean owner      = turf.getOwnerGangId() != null && turf.getOwnerGangId() == gangId;
			boolean challenger = state.getChallengerGangId() != null && state.getChallengerGangId() == gangId;
			if (owner || challenger) {
				refuse(event, player, "Gang_In_Fight",
				       "&cYour gang is in a turf fight at &f%turf%&c: gang teleports are off until it ends.", turf);
				return;
			}
		}
	}

	private void refuse(TeleportEvent event, Player player, String key, String fallback, Turf turf) {
		event.setCancelled(true);
		player.sendMessage(text.text("Teleport_Guard." + key, fallback, "turf", turf.getDisplayName()));
	}

	/** The x/z distance from {@code location} to the region (0 inside) is at most {@code buffer} blocks. */
	static boolean withinBuffer(CuboidRegion region, Location location, int buffer) {
		long dx = Math.max(0, Math.max(region.getMinX() - location.getBlockX(), location.getBlockX() - region.getMaxX()));
		long dz = Math.max(0, Math.max(region.getMinZ() - location.getBlockZ(), location.getBlockZ() - region.getMaxZ()));
		return dx * dx + dz * dz <= (long) buffer * buffer;
	}
}
