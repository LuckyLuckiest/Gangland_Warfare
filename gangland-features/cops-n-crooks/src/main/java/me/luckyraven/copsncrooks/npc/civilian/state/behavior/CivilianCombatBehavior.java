package me.luckyraven.copsncrooks.npc.civilian.state.behavior;

import me.luckyraven.copsncrooks.entity.npc.AbstractNpc;
import me.luckyraven.copsncrooks.npc.civilian.CivilianState;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import me.luckyraven.copsncrooks.npc.civilian.state.CivilianBehavior;
import me.luckyraven.util.downed.DownedPlayerRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Combat behavior: the civilian pursues and attacks its designated target (player or NPC entity).
 * <p>
 * On every tick it resolves the target from {@link CivilianNpc#getTargetPlayerId()} (player priority) or
 * {@link CivilianNpc#getTargetEntity()} (NPC-to-NPC fallback), reusing the full {@link AbstractNpc#attack} /
 * {@link AbstractNpc#attackEntity} pipeline. Reverts to {@link CivilianState#IDLE} when the target goes offline/dies,
 * moves out of attack range, or navigation becomes permanently hopeless.
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

		double attackRange = npc.getTypeConfig().ai().attackRange();
		double distance    = npc.distanceTo(target);

		// Lost track of the target — give up only at 4× attack range so the NPC commits to pursuit
		if (distance > attackRange * 4) {
			clearTarget(npc);
			npc.transitionTo(CivilianState.IDLE);
			return;
		}

		// Navigate unless we should hold a ranged firing position
		if (!npc.shouldHoldPursuitPosition(target)) {
			Location pursuitLoc = npc.isNavigationHopeless() ?
			                      npc.resolveHopelessFallbackLocation(target) :
			                      npc.resolvePursuitLocation(target);

			if (pursuitLoc != null) {
				npc.navigateTo(pursuitLoc);
			} else {
				clearTarget(npc);
				npc.transitionTo(CivilianState.IDLE);
				return;
			}
		} else {
			npc.stopNavigation();
		}

		// Attack when close enough and cooldown elapsed
		if (distance <= attackRange && npc.canAttack()) {
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
		// targets intentionally preserved so IDLE can re-engage if the target returns in range
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

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
