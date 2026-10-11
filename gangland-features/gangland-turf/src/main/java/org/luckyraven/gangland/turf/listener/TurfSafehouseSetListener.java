package org.luckyraven.gangland.turf.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.events.gang.GangSafehouseSetEvent;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.TurfRelation;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** W5-T: a gang cannot set a safehouse inside a rival turf or any turf under attack. */
@ListenerHandler
public final class TurfSafehouseSetListener implements Listener {

	/** The event carries no player; the setter is whoever stands within 3 blocks of the spot. */
	private static final double SETTER_RADIUS_SQUARED = 9.0;

	private final TurfManager        turfs;
	private final GangLookupContract gangs;
	private final TurfMessages       text;

	public TurfSafehouseSetListener(TurfManager turfs, GangLookupContract gangs, TurfMessages text) {
		this.turfs = turfs;
		this.gangs = gangs;
		this.text  = text;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onSafehouseSet(GangSafehouseSetEvent event) {
		Location where = event.location();
		Turf     turf  = turfs.findAt(where);
		if (turf == null) {
			return;
		}
		boolean rival = TurfRelation.of(turf, event.gangId(), gangs) == TurfRelation.RIVAL;
		if (!rival && !TurfTeleportGuardListener.underAttack(turfs, turf)) {
			return;
		}
		event.setCancelled(true);
		// ponytail: nearest-player guess; an exact setter on the event is an api addition for a later release
		for (Player player : where.getWorld().getPlayers()) {
			if (player.getLocation().distanceSquared(where) <= SETTER_RADIUS_SQUARED) {
				player.sendMessage(text.text("Teleport_Guard.Safehouse_Refused",
				                             "&cYou cannot set a safehouse inside &f%turf%&c.",
				                             "turf", turf.getDisplayName()));
			}
		}
	}
}
