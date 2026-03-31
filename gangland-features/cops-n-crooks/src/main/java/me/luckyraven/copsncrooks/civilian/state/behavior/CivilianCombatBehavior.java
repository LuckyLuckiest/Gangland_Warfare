package me.luckyraven.copsncrooks.civilian.state.behavior;

import me.luckyraven.copsncrooks.civilian.CivilianNpc;
import me.luckyraven.copsncrooks.civilian.CivilianState;
import me.luckyraven.copsncrooks.civilian.state.CivilianBehavior;
import me.luckyraven.copsncrooks.entity.npc.AbstractNpc;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Combat behavior: the civilian pursues and attacks its designated target player.
 * <p>
 * On every tick it resolves the target from {@link CivilianNpc#getTargetPlayerId()}, reusing the full
 * {@link AbstractNpc#attack} pipeline (gangland weapon → vanilla ranged → melee, with reload). Reverts to
 * {@link CivilianState#IDLE} when the target goes offline, moves out of attack range, or navigation becomes permanently
 * hopeless.
 */
public class CivilianCombatBehavior implements CivilianBehavior {

	@Override
	public void onEnter(CivilianNpc npc) {
		// Navigation is started on the first tick once a target is confirmed
	}

	@Override
	public void tick(CivilianNpc npc) {
		Player target = resolveTarget(npc);

		if (target == null) {
			npc.transitionTo(CivilianState.IDLE);
			return;
		}

		double attackRange = npc.getTypeConfig().ai().attackRange();
		double distance    = npc.distanceTo(target);

		// Lost track of the target — give up
		if (distance > attackRange * 2) {
			npc.setTargetPlayerId(null);
			npc.transitionTo(CivilianState.IDLE);
			return;
		}

		if (npc.isNavigationHopeless()) {
			npc.transitionTo(CivilianState.IDLE);
			return;
		}

		// Navigate unless we should hold a ranged firing position
		if (!npc.shouldHoldPursuitPosition(target)) {
			Location pursuitLoc = npc.isNavigationHopeless()
			                      ? npc.resolveHopelessFallbackLocation(target)
			                      : npc.resolvePursuitLocation(target);

			if (pursuitLoc != null) {
				npc.navigateTo(pursuitLoc);
			}
		} else {
			npc.stopNavigation();
		}

		// Attack when close enough and cooldown elapsed
		if (distance <= attackRange && npc.canAttack()) {
			npc.attack(target);
		}
	}

	@Override
	public void onExit(CivilianNpc npc) {
		npc.stopNavigation();
		npc.setTargetPlayerId(null);
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	@Nullable
	private Player resolveTarget(CivilianNpc npc) {
		UUID targetId = npc.getTargetPlayerId();
		if (targetId == null) return null;

		Player player = Bukkit.getPlayer(targetId);
		if (player == null || !player.isOnline()) {
			npc.setTargetPlayerId(null);
			return null;
		}

		return player;
	}
}
