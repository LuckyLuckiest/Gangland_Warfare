package org.luckyraven.gangland.civilians.npc.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.npc.AbstractNpc;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.config.CivilianAIBehaviorConfig;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.state.CivilianBehavior;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;

import java.util.UUID;

/**
 * Combat behavior: the civilian hunts and attacks its designated target (player or NPC entity) together with its
 * squad.
 * <p>
 * On every tick it resolves the target from {@link CivilianNpc#getTargetEntity()} (self-defense priority) or
 * {@link CivilianNpc#getTargetPlayerId()}, moves through Keystone's squad pursuit ({@link AbstractNpc#pursue}: chase
 * while anyone in the squad sees the target, otherwise search from the last-known position; ranged NPCs hold and shoot
 * inside their band) and reuses the full {@link AbstractNpc#attack} / {@link AbstractNpc#attackEntity} pipeline.
 * Reverts to {@link CivilianState#IDLE} when the target goes offline/dies, or when nobody in its squad has seen the
 * target for {@code AI.Combat.Search_Seconds}.
 */
public class CivilianCombatBehavior implements CivilianBehavior {

	@Override
	public void onEnter(CivilianNpc npc) {
		// Navigation is started on the first tick once a target is confirmed
	}

	@Override
	public void tick(CivilianNpc npc) {
		LivingEntity target = resolveTarget(npc);

		if (target == null) {
			npc.transitionTo(CivilianState.IDLE);
			return;
		}

		CivilianAIBehaviorConfig ai    = npc.getTypeConfig().ai();
		NpcSquad                 squad = squadFor(npc, target);

		npc.pursue(target, squad, ai.alertRange());

		// Search window: nobody in the squad has seen the target for too long - give up (onExit leaves the squad)
		if (squad.millisSinceSighting() > ai.searchSeconds() * 1000L) {
			clearTarget(npc);
			npc.transitionTo(CivilianState.IDLE);
			return;
		}

		// Attack only with line of sight - never through a wall (matches the cops' LOS gate)
		if (npc.distanceTo(target) <= ai.attackRange() && npc.canAttack() && npc.hasLineOfSight(target)) {
			if (target instanceof Player player) {
				npc.attack(player);
			} else {
				npc.attackEntity(target);
			}
		}
	}

	@Override
	public void onExit(CivilianNpc npc) {
		npc.stopNavigation();
		npc.leaveSquad();
		// targets intentionally preserved so IDLE can re-engage if the target returns in range
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	/**
	 * The civilian's squad against {@code target}: the faction squad a hit put it in, or - when it entered combat
	 * another way (turf defender retarget, idle re-engage) or its target switched - a squad of its own, told where the
	 * target is now (the civilian counterpart of a cop's wanted start).
	 */
	private NpcSquad squadFor(CivilianNpc npc, LivingEntity target) {
		NpcSquad squad = npc.getSquad();
		if (squad != null && target.getUniqueId().equals(npc.getSquadTargetId())) return squad;

		// ponytail: a civilian that entered combat without a hit hunts alone (defenders of one turf do not share a
		// squad); route those entries through CivilianService's faction squads if they should team up
		NpcSquad own = new NpcSquad();
		own.reportSighting(target.getLocation());
		npc.joinSquad(own, target.getUniqueId());
		return own;
	}

	/**
	 * Resolves the current combat target. Entity targets (self-defense) take priority over the player target so the
	 * civilian fights back against whoever attacked it before resuming any ongoing offence.
	 */
	@Nullable
	private LivingEntity resolveTarget(CivilianNpc npc) {
		// 1. Entity target queue (self-defense / last attacker has priority)
		LivingEntity entityTarget = npc.getTargetEntity(); // auto-cleans stale queue entries
		if (entityTarget != null) return entityTarget;

		// 2. Player target (fallback — civilian was hunting this player before being attacked)
		UUID targetId = npc.getTargetPlayerId();
		if (targetId != null) {
			Player player = Bukkit.getPlayer(targetId);
			if (player != null && player.isOnline() && !player.isDead() && !DownedPlayerRegistry.isDowned(targetId)) {
				return player;
			}
			npc.setTargetPlayerId(null);
		}

		return null;
	}

	private void clearTarget(CivilianNpc npc) {
		npc.setTargetPlayerId(null);
		npc.setTargetEntity(null);
	}
}
