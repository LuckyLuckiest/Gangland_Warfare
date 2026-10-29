package org.luckyraven.gangland.turf.listener.powerups;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.luckyraven.gangland.civilians.events.CivilianDeathEvent;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupNpc;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.gangland.turf.npc.guard.TurfOwnerAlerts;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * W7-E (D28=A): feeds guard deaths, Quartermaster deaths and attacks on the Quartermaster into {@link TurfOwnerAlerts}.
 * Only plain Bukkit and civilians event types, so no Citizens gate is needed.
 */
@ListenerHandler
public final class TurfOwnerAlertListener implements Listener {

	private final TurfOwnerAlerts      alerts;
	private final TurfDefenderDeployer defenders;
	private final TurfPowerupManager   powerups;
	private final TurfManager          turfs;
	private final GangMembership       gangs;

	public TurfOwnerAlertListener(TurfOwnerAlerts alerts, TurfDefenderDeployer defenders, TurfPowerupManager powerups,
	                              TurfManager turfs, GangMembership gangs) {
		this.alerts    = alerts;
		this.defenders = defenders;
		this.powerups  = powerups;
		this.turfs     = turfs;
		this.gangs     = gangs;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCivilianDeath(CivilianDeathEvent e) {
		// The body is gone by MONITOR: Citizens despawns the NPC in its LOW death handler, so npc.getEntity() is null.
		CivilianNpc npc = e.getCivilianNpc();
		int guardTurf = defenders.turfOf(npc);
		if (guardTurf >= 0) {
			alerts.guardDown(guardTurf, npc);
			return;
		}
		TurfPowerupNpc qm = powerups.getByCivilian(npc);
		if (qm != null) {
			powerups.onKilled(qm.getData().getTurfId());
			alerts.quartermasterDown(qm.getData().getTurfId(), npc);
		}
	}

	// ponytail: Bartizan raytrace hits on the Quartermaster are seen only through the EntityDamageByEntityEvent Bartizan
	// raises; turf has no Bartizan edge.
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent e) {
		TurfPowerupNpc qm = powerups.getByEntity(e.getEntity());
		if (qm == null) return;
		int turfId = qm.getData().getTurfId();

		Entity attacker = e.getDamager();
		if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
			attacker = shooter;
		}
		if (attacker == null || attacker.equals(e.getEntity())) return;
		if (defenders.findOwningTurfId(attacker) == turfId) return;
		if (attacker instanceof Player p && friendly(p, turfId)) return;
		alerts.quartermasterHit(turfId, attacker.getName());
	}

	private boolean friendly(Player player, int turfId) {
		Turf    turf  = turfs.get(turfId);
		Integer owner = turf == null ? null : turf.getOwnerGangId();
		if (owner == null) return false;
		int gang = gangs.gangIdOf(player.getUniqueId());
		return gang >= 0 && (gang == owner || gangs.gangsAllied(gang, owner));
	}
}
