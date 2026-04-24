package me.luckyraven.copsncrooks.listener.turf;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.turf.TurfPowerupManager;
import me.luckyraven.copsncrooks.npc.turf.TurfPowerupNpc;
import me.luckyraven.copsncrooks.npc.turf.defender.TurfDefenderDeployer;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.gang.contract.UserLookupContract;
import me.luckyraven.gang.user.User;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.manager.TurfManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Cancels friendly fire from the owning gang against the per-turf Quartermaster and any defenders deployed for the same
 * turf. Runs at {@link EventPriority#HIGH} so the cancel lands before
 * {@link me.luckyraven.copsncrooks.listener.civilian.CivilianDamageListener}'s MONITOR-priority self-defense trigger
 * can flip the civilian into COMBAT mode against an ally.
 *
 * <p>The match is resolved both ways:
 * <ul>
 *   <li>The damaged entity must be a Quartermaster (via {@link TurfPowerupManager#getByEntity}) or a defender
 *       (via {@link TurfDefenderDeployer#findOwningTurfId}) — anything else this listener ignores.</li>
 *   <li>The damager must be a {@link Player} (or a projectile shot by one) whose gang id matches the turf's
 *       owning gang id.</li>
 * </ul>
 * Damage from non-owning gangs (the actual attackers) passes through untouched and the civilian's normal
 * self-defense kicks in as designed.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfFriendlyFireListener implements Listener {

	private final TurfPowerupManager   powerupNpcs;
	private final TurfDefenderDeployer defenders;
	private final TurfManager          turfs;
	private final UserLookupContract   users;
	private final GangLookupContract   gangs;

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent event) {
		Entity damaged = event.getEntity();

		int turfId = resolveTurfId(damaged);
		if (turfId < 0) return;

		Turf turf = turfs.get(turfId);
		if (turf == null || turf.isUnclaimed()) return;
		int ownerGangId = turf.getOwnerGangId();

		Player attacker = resolvePlayerAttacker(event.getDamager());
		if (attacker == null) return;

		User<Player> user = users.findByPlayer(attacker);
		if (user == null || !user.hasGang()) return;

		// Friendly = same gang OR allied gang. Allies share the turf's NPCs so attacks between them never
		// flip the civilian into self-defense, and damage is fully cancelled (matches gang-allies' existing
		// no-damage rule against each other).
		if (!isFriendly(user.getGangId(), ownerGangId)) return;

		event.setCancelled(true);
	}

	private boolean isFriendly(int attackerGangId, int ownerGangId) {
		if (attackerGangId == ownerGangId) return true;
		Gang ownerGang    = gangs.findById(ownerGangId);
		Gang attackerGang = gangs.findById(attackerGangId);
		return ownerGang != null && attackerGang != null && attackerGang.isAlly(ownerGang);
	}

	private int resolveTurfId(Entity damaged) {
		TurfPowerupNpc qm = powerupNpcs.getByEntity(damaged);
		if (qm != null) return qm.getData().getTurfId();
		return defenders.findOwningTurfId(damaged);
	}

	private Player resolvePlayerAttacker(Entity damager) {
		if (damager instanceof Player p) return p;
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) return p;
		return null;
	}
}
