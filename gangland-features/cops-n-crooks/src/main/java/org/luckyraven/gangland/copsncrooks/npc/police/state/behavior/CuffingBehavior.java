package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.events.police.CuffedEvent;
import org.luckyraven.gangland.copsncrooks.events.police.DuringCuffingEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;

import java.util.UUID;

/**
 * Cop attempts to cuff the target player.
 * <p>
 * Only one cop per group may hold the cuff lock for a given target at a time. The lock is acquired at the start of the
 * wind-up and held through the entire {@link DuringCuffingEvent} sequence. When {@link CopNpc#attemptCuff} succeeds the
 * cop transitions to {@link CopState#RETURNING}; if the target moved out of range before the attempt, the lock is
 * released and the cop returns to {@link CopState#PURSUING}, allowing the next closest cop in the group to acquire the
 * lock.
 */
public class CuffingBehavior implements CopBehavior {

	private final double            cuffRadius;
	private final int               maxAttempts;
	private final long              cuffingCooldown;
	private final int               aiTickRate;
	private final CuffLockRegistry  cuffLockRegistry;
	private final DetainmentService detainmentService;

	private long cuffingTicks;
	private UUID claimedPlayer;
	/** Cuff attempts on {@link #failTarget} that failed or that he broke out of; kept across re-entries. */
	private int  failedCuffs;
	private UUID failTarget;

	public CuffingBehavior(double cuffRadius, int maxAttempts, long cuffingCooldown, int aiTickRate,
	                       CuffLockRegistry cuffLockRegistry, DetainmentService detainmentService) {
		this.cuffRadius        = cuffRadius;
		this.maxAttempts       = maxAttempts;
		this.cuffingCooldown   = cuffingCooldown;
		this.aiTickRate        = aiTickRate;
		this.cuffLockRegistry  = cuffLockRegistry;
		this.detainmentService = detainmentService;

		reset();
	}

	@Override
	public void tick(CopNpc cop) {
		// Cuffing only applies to players; entity targets (hostile civilians) go straight to COMBAT
		if (cop.getTargetPlayerId() == null && cop.getTargetEntity() != null) {
			cop.transitionTo(CopState.COMBAT);
			return;
		}

		Player target = cop.getTargetPlayerId() != null ? Bukkit.getPlayer(cop.getTargetPlayerId()) : null;
		if (target == null || !target.isOnline()) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		if (detainmentService.isRestrained(target)) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		UUID copId    = cop.getNpc().getUniqueId();
		UUID targetId = target.getUniqueId();
		if (!targetId.equals(failTarget)) {
			failTarget  = targetId;
			failedCuffs = 0;
		}

		if (claimedPlayer == null) {
			if (!cuffLockRegistry.tryAcquire(targetId, copId)) {
				cop.transitionTo(CopState.PURSUING);
				return;
			}

			claimedPlayer = targetId;
			reset();
			cop.stopNavigation();
		} else if (!cuffLockRegistry.isOwner(targetId, copId)) {
			cop.transitionTo(CopState.PURSUING);
			return;
		}

		double distance = cop.distanceTo(target);

		// Only leave cuffing if the target actually escapes the cuffing zone; breaking out of a started cuff counts
		if (distance > cuffRadius || !cop.hasLineOfSight(target)) {
			failCuff(cop, targetId);
			return;
		}

		if (cuffingTicks > 0) {
			// Pass remaining time in game ticks so the listener's seconds display is accurate
			long remainingGameTicks = cuffingTicks * aiTickRate;
			var duringCuffingEvent = new DuringCuffingEvent(cop, target, cuffRadius, maxAttempts,
			                                                cuffingCooldown * aiTickRate, remainingGameTicks);
			Bukkit.getPluginManager().callEvent(duringCuffingEvent);
			cuffingTicks--;
			return;
		}

		// Wind-up complete - attempt the cuff
		boolean success = cop.attemptCuff(target);

		if (success) {
			var cuffedEvent = new CuffedEvent(cop, target, cuffRadius, maxAttempts);
			Bukkit.getPluginManager().callEvent(cuffedEvent);
			// Transfer cuff-lock ownership to the GUARDING state without releasing:
			// clear claimedPlayer so onExit's releaseLock() is a no-op, then stash the
			// target on the cop for GuardingBehavior to pick up.
			UUID cuffedTargetId = claimedPlayer;
			claimedPlayer = null;
			failedCuffs   = 0;
			cop.setGuardedPlayerId(cuffedTargetId);
			cop.transitionTo(CopState.GUARDING);
			return;
		}

		// Target moved out of range or lost LOS at the last moment.
		// Release the lock and return to pursuit so the next closest cop may try.
		failCuff(cop, targetId);
	}

	/**
	 * A cuff failed or the target broke out of it: back to pursuit, or, after {@code Max_Cuff_Attempts} of them, he is
	 * resisting and the group fights (CJ-23).
	 */
	private void failCuff(CopNpc cop, UUID targetId) {
		if (++failedCuffs < maxAttempts) {
			cop.transitionTo(CopState.PURSUING);
			return;
		}

		failedCuffs = 0;
		if (cop.getGroup() != null) cop.getGroup().escalate(targetId);
		cop.setCombatForced(true);
		cop.transitionTo(CopState.COMBAT);
	}

	@Override
	public void onEnter(CopNpc cop) {
		UUID targetId = cop.getTargetPlayerId();
		if (targetId == null) {
			cop.transitionTo(CopState.PURSUING);
			return;
		}

		// Brand-new reservation: always restart from the beginning of the cuff wind-up
		claimedPlayer = null;
		reset();
		cop.stopNavigation();
	}

	@Override
	public void onExit(CopNpc cop) {
		releaseLock(cop);
		reset();
	}

	private void releaseLock(CopNpc cop) {
		UUID targetId = claimedPlayer;

		if (targetId == null) return;

		cuffLockRegistry.release(targetId, cop.getNpc().getUniqueId());
		claimedPlayer = null;
	}

	private void reset() {
		this.cuffingTicks = cuffingCooldown;
	}
}
