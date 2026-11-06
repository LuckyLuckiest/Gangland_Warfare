package org.luckyraven.gangland.turf.listener.powerups;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupNpc;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Cancels Bukkit-damage friendly fire on turf NPCs: an owning or allied member hitting a Quartermaster or a defender,
 * and a guard hitting a guard of its own or an allied owner. Lives in the turf module so it works without the cops
 * module. The Bartizan weapon-impact half stays in the cops listener, which needs Bartizan.
 *
 * <p>Runs at {@link EventPriority#HIGH} for player hits so the cancel lands before the civilian self-defence listener
 * can flip the civilian into COMBAT mode against an ally, and at {@link EventPriority#LOW} for guard-on-guard rounds.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfGuardFriendlyFireListener implements Listener {

	private final TurfPowerupManager   powerupNpcs;
	private final TurfDefenderDeployer defenders;
	private final TurfManager          turfs;
	private final UserLookupContract   users;
	private final GangMembership       membership;

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

	/**
	 * Stray rounds and melee from a turf NPC: a defender or Quartermaster that fires at a cop often has a protected
	 * member standing in the line of fire, so the shot must not hit that member. Also covers guard-on-guard (T-287).
	 */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onNpcDamage(EntityDamageByEntityEvent event) {
		if (isNpcFriendlyFire(resolveShooter(event.getDamager()), event.getEntity())) event.setCancelled(true);
	}

	private boolean isNpcFriendlyFire(Entity shooter, Entity victim) {
		if (shooter == null || victim == null) return false;

		int turfId = resolveTurfId(shooter);
		if (turfId < 0) return false;

		Turf turf = turfs.get(turfId);
		if (turf == null || turf.isUnclaimed()) return false;

		// T-287: guards are Citizens PLAYER entities with no User, so guard-on-guard rounds used to pass.
		int victimTurfId = resolveTurfId(victim);
		if (victimTurfId >= 0) {
			Turf victimTurf = turfs.get(victimTurfId);
			return victimTurf != null && !victimTurf.isUnclaimed() &&
				   isFriendly(turf.getOwnerGangId(), victimTurf.getOwnerGangId());
		}

		if (!(victim instanceof Player target)) return false;

		User<Player> user = users.findByPlayer(target);
		if (user == null || !user.hasGang()) return false;

		return isFriendly(user.getGangId(), turf.getOwnerGangId());
	}

	private boolean isFriendly(int attackerGangId, int ownerGangId) {
		if (attackerGangId == ownerGangId) return true;
		return membership.gangsAllied(attackerGangId, ownerGangId);
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

	private Entity resolveShooter(Entity damager) {
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) return shooter;
		return damager;
	}
}
