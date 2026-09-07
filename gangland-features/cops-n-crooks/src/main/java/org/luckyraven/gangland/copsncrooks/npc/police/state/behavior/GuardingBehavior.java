package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;

import java.util.UUID;

/**
 * Cop stays near a player it just cuffed until the transit timer commits them to jail (or they are released by bribe /
 * admin). Preserves the cuff-lock so bribe listeners can resolve "which cop am I right-clicking".
 */
public class GuardingBehavior implements CopBehavior {

	private final double            guardRadius;
	private final CuffLockRegistry  cuffLockRegistry;
	private final DetainmentService detainmentService;

	public GuardingBehavior(double guardRadius, CuffLockRegistry cuffLockRegistry,
	                        DetainmentService detainmentService) {
		this.guardRadius       = guardRadius;
		this.cuffLockRegistry  = cuffLockRegistry;
		this.detainmentService = detainmentService;
	}

	@Override
	public void tick(CopNpc cop) {
		UUID guardedId = cop.getGuardedPlayerId();
		if (guardedId == null) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		Player target = Bukkit.getPlayer(guardedId);
		if (target == null || !target.isOnline()) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		// Player is no longer handcuffed — either released (bribe / admin) or committed to jail.
		// Either way the guard job is done.
		if (!detainmentService.isHandcuffed(target)) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		// Keep the cop within guardRadius of the player. If we drift, path toward them.
		double distance = cop.distanceTo(target);
		if (distance > guardRadius) {
			cop.navigateTo(target.getLocation());
		} else {
			cop.stopNavigation();
		}
	}

	@Override
	public void onEnter(CopNpc cop) {
		UUID guardedId = cop.getGuardedPlayerId();
		if (guardedId == null) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		// The lock was transferred from CuffingBehavior (claimedPlayer cleared without release).
		// If for any reason we are not the owner, bail out — another cop must have taken over.
		if (!cuffLockRegistry.isOwner(guardedId, cop.getNpc().getUniqueId())) {
			cop.setGuardedPlayerId(null);
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		cop.stopNavigation();
	}

	@Override
	public void onExit(CopNpc cop) {
		UUID guardedId = cop.getGuardedPlayerId();
		if (guardedId != null) {
			cuffLockRegistry.release(guardedId, cop.getNpc().getUniqueId());
			cop.setGuardedPlayerId(null);
		}
	}
}
