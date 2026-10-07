package org.luckyraven.gangland.copsncrooks.listener.detainment;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.events.npc.CopDeathEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.events.wanted.WantedStartEvent;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;
import org.luckyraven.bartizan.api.raytrace.WeaponRaytracer;

@ListenerHandler
@RequiredArgsConstructor
public class CopListener implements Listener {

	private final CopManager copManager;

	/**
	 * Starts cop pursuit when a player becomes wanted.
	 *
	 * @param event the wanted start event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedStart(WantedStartEvent event) {
		copManager.onWantedStart(event.getPlayer(), event.getWanted(), event.getCause());
	}

	/**
	 * Stops cop pursuit when a player is no longer wanted.
	 *
	 * @param event the wanted end event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedEnd(WantedEndEvent event) {
		copManager.onWantedEnd(event.getPlayer());
	}

	/**
	 * Updates cop assignments when wanted level changes.
	 *
	 * @param event the wanted level change event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWantedChange(WantedLevelChangeEvent event) {
		copManager.onWantedLevelChange(event.getPlayer(), event.getWanted(), event.getOldLevel(), event.getNewLevel());
	}

	/**
	 * Cleans up cops when a player leaves the server.
	 *
	 * @param event the player quit event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		copManager.removeCopAttacker(player.getUniqueId());
		copManager.onWantedEnd(player);
	}

	/**
	 * Removes a player from the cop-attacker registry when they die so that cops do not continue attacking them after
	 * respawn.
	 *
	 * @param event the player death event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDeath(PlayerDeathEvent event) {
		copManager.removeCopAttacker(event.getEntity().getUniqueId());
	}

	/**
	 * Removes a player from the cop-attacker registry when they are downed (GTA-style death that prevents actual
	 * death). Mirrors the behavior of {@link #onPlayerDeath} for the downed state.
	 *
	 * @param event the player downed event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDowned(PlayerDownedEvent event) {
		copManager.removeCopAttacker(event.getPlayer().getUniqueId());
	}

	/**
	 * Handles a player attacking a cop NPC - forces the cop into combat mode.
	 *
	 * @param event the damage event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCopDamaged(EntityDamageByEntityEvent event) {
		Entity victim = event.getEntity();

		if (!copManager.isCopNpc(victim)) return;

		Entity damager = event.getDamager();
		// Before the raytrace skip: Bartizan applies a shot's damage through victim.damage(amount, shooter), which
		// lands here, so this one guard covers melee, arrows and gunfire alike.
		shieldBlock(event, victim, damager);

		// Weapon-system shots route through WeaponRaytraceImpactEvent (see onWeaponRaytraceImpact below).
		// Skip here to avoid double-processing the same shot via two separate code paths.
		if (WeaponRaytracer.isRaytraceDamageInProgress()) return;

		// T-KR4 (review M4): CitizensAPI.getNPCRegistry() was unguarded here — NpcSupport.isNpc() is the T-M3
		// established, never-throws replacement (false when Citizens is absent, so a genuine player attacker
		// still resolves instead of falling through to the non-player branch below).
		Player attacker;
		if (damager instanceof Player player && !NpcSupport.isNpc(player)) {
			attacker = player;
		} else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player
		           && !NpcSupport.isNpc(player)) {
			attacker = player;
		} else {
			// Friendly fire: cancel damage when a cop is hit by another cop's attack
			boolean isCopFire = copManager.isCopNpc(damager) ||
			                    (damager instanceof Projectile p && p.getShooter() instanceof Entity shooterEntity &&
			                     copManager.isCopNpc(shooterEntity));
			if (isCopFire) {
				event.setCancelled(true);
				return;
			}

			// Non-cop NPC attacker — queue it so this cop engages after finishing with the player
			CopNpc attackedCop = copManager.findCopByEntity(victim);
			if (attackedCop != null) {
				LivingEntity entityAttacker = null;
				if (damager instanceof LivingEntity le) {
					entityAttacker = le;
				} else if (damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity le) {
					entityAttacker = le;
				}
				if (entityAttacker != null) {
					attackedCop.addEntityAttacker(entityAttacker);
				}
			}
			return;
		}

		CopNpc cop = copManager.findCopByEntity(victim);
		if (cop == null) return;

		// Alert system: put ALL cops for this player into combat mode
		copManager.onCopAttackedAlert(cop, attacker);
	}

	/**
	 * A cop whose role blocks (the Defender's shield) takes {@code Block_Fraction} off a hit coming from inside its
	 * front cone. Where the hit comes from is the shooter for a projectile, else the damager. Bartizan credits area
	 * damage (explosions, fire, beams) to the shooter, so the cone is judged by who attacked, not where it landed.
	 */
	private void shieldBlock(EntityDamageByEntityEvent event, Entity victim, Entity damager) {
		CopNpc  cop  = copManager.findCopByEntity(victim);
		CopRole role = cop != null ? cop.getRole() : null;
		if (role == null) return;

		Entity source = damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
		                ? shooter : damager;
		if (role.blocks(victim.getLocation(), source.getLocation()))
			event.setDamage(event.getDamage() * (1 - role.blockFraction()));
	}

	/**
	 * Handles weapon-system shots against cop NPCs. Canonical hook for any gangland weapon hitting a cop. Mirrors the
	 * legacy {@link #onCopDamaged} branches but operates on {@link WeaponRaytraceImpactEvent}, which is fired by
	 * {@code WeaponRaytracer} for every unified hit detection result. Critical: this handler also enforces the cop
	 * friendly-fire cancel — if both shooter and victim are cop NPCs, {@code event.setCancelled(true)} stops the
	 * raytracer from applying damage.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onWeaponRaytraceImpact(WeaponRaytraceImpactEvent event) {
		Entity victim = event.getHitEntity();
		if (victim == null) return;

		if (!copManager.isCopNpc(victim)) return;

		LivingEntity shooter = event.getShooter();
		if (shooter == null) return;

		// Friendly fire: cancel when a cop is hit by another cop's weapon
		if (copManager.isCopNpc(shooter)) {
			event.setCancelled(true);
			return;
		}

		if (shooter instanceof Player attacker && !NpcSupport.isNpc(attacker)) {
			CopNpc cop = copManager.findCopByEntity(victim);
			if (cop == null) return;
			copManager.onCopAttackedAlert(cop, attacker);
			return;
		}

		// Non-player, non-cop LivingEntity attacker (hostile civilian, etc.)
		CopNpc attackedCop = copManager.findCopByEntity(victim);
		if (attackedCop != null) {
			attackedCop.addEntityAttacker(shooter);
		}
	}

	/**
	 * Reports a killed cop down to its squads (the radio's "Officer down" and a backup request), marks it for removal
	 * and clears its drops. The dying entity is already invalid here, so the cop is found without a validity check.
	 * <p>
	 * The cop is marked, not destroyed: {@code destroy()} despawns with reason PLUGIN, which deletes the body mid-death
	 * (no death animation). Citizens' own LOW handler despawns it with reason DEATH and keeps the body; the next AI tick
	 * releases the marked cop.
	 * <p>
	 * Runs at LOWEST so the squad is told the cop is down, and the cop is marked, before Citizens' own death listener
	 * (LOW) despawns the NPC. The cop is still matched after that despawn through the Citizens registry (see
	 * {@code findDyingCop}). A cop walking home (RETURNING) left every
	 * squad, so it is reported down to its group's squad (T-121).
	 *
	 * @param event the death event
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onCopDeath(EntityDeathEvent event) {
		CopNpc cop = copManager.findDyingCop(event.getEntity());
		if (cop == null) return;

		NpcSquad squad = cop.getCurrentSquad();
		if (squad != null) squad.memberDown(cop);
		CopGroup group = cop.getGroup();
		if (group != null && group.getSquad() != squad) {
			NpcSquad groupSquad = group.getSquad();
			if (squad == null) groupSquad.add(cop); // RETURNING: in no squad, still the group's casualty
			if (groupSquad.members().contains(cop)) groupSquad.memberDown(cop);
		}
		cop.markForRemoval();

		event.getDrops().clear();
		event.setDroppedExp(0);

		if (event instanceof PlayerDeathEvent playerDeathEvent) {
			playerDeathEvent.setKeepInventory(true);
		}

		Bukkit.getPluginManager().callEvent(new CopDeathEvent(cop, event.getEntity().getKiller()));
	}
}