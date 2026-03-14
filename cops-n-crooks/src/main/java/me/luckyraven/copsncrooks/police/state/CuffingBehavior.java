package me.luckyraven.copsncrooks.police.state;

import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.events.police.CuffedEvent;
import me.luckyraven.copsncrooks.events.police.DuringCuffingEvent;
import me.luckyraven.copsncrooks.police.npc.CopNpc;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Cop attempts to cuff the target player. Escalates to combat on repeated failure.
 * <p>
 * Only one cop per player may be in this state at a time; ownership is coordinated via the shared {@code cuffLock} set
 * injected at construction. A cop that cannot claim the lock immediately falls back to {@link CopState#PURSUING}
 * without interfering with the cop that holds it.
 */
public class CuffingBehavior implements CopBehavior {

	private final double            cuffRadius;
	private final int               maxAttempts;
	/**
	 * Countdown duration in AI ticks (already divided by aiTickRate at construction).
	 */
	private final long              cuffingCooldown;
	/**
	 * Stored so the event receives remaining time in game ticks, not AI ticks.
	 */
	private final int               aiTickRate;
	/**
	 * Shared across all cop instances - only the cop that inserted its target UUID may cuff.
	 */
	private final Set<UUID>         cuffLock;
	private final DetainmentService detainmentService;

	private int  attemptCount;
	private long cuffingTicks;
	/**
	 * Non-null only while this instance holds the cuff lock for a player.
	 */
	private UUID claimedPlayer;

	public CuffingBehavior(double cuffRadius, int maxAttempts, long cuffingCooldown, int aiTickRate, Set<UUID> cuffLock,
						   DetainmentService detainmentService) {
		this.cuffRadius        = cuffRadius;
		this.maxAttempts       = maxAttempts;
		this.cuffingCooldown   = cuffingCooldown;
		this.aiTickRate        = aiTickRate;
		this.cuffLock          = cuffLock;
		this.detainmentService = detainmentService;

		reset();
	}

	@Override
	public void tick(CopNpc cop, Player target) {
		if (target == null || !target.isOnline()) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		if (detainmentService.isRestrained(target)) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		double distance = cop.distanceTo(target);

		if (distance > cuffRadius || !cop.hasLineOfSight(target)) {
			cop.transitionTo(CopState.PURSUING);
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
			attemptCount = 0;
			var cuffedEvent = new CuffedEvent(cop, target, cuffRadius, maxAttempts);
			Bukkit.getPluginManager().callEvent(cuffedEvent);
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		attemptCount++;
		cuffingTicks = cuffingCooldown;

		if (!(attemptCount >= maxAttempts && cop.getTierConfig().canUseWeapons())) return;

		cop.transitionTo(CopState.COMBAT);
	}

	@Override
	public void onEnter(CopNpc cop) {
		reset();

		// Claim the cuff lock before doing anything else - if another cop already holds it, back off
		UUID targetId = cop.getTargetPlayerId();
		if (targetId == null || !cuffLock.add(targetId)) {
			cop.transitionTo(CopState.PURSUING);
			return;
		}

		// If the player is already restrained, release the lock and return to spawn
		Player target = Bukkit.getPlayer(targetId);
		if (target != null && detainmentService.isRestrained(target)) {
			cuffLock.remove(targetId);
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		claimedPlayer = targetId;
		cop.stopNavigation();
	}

	@Override
	public void onExit(CopNpc cop) {
		releaseLock();
		reset();
	}

	private void releaseLock() {
		if (claimedPlayer != null) {
			cuffLock.remove(claimedPlayer);
			claimedPlayer = null;
		}
	}

	private void reset() {
		this.attemptCount = 0;
		this.cuffingTicks = cuffingCooldown;
	}
}
