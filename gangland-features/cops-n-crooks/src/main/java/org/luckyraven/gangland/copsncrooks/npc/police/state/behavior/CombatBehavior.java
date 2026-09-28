package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.keystone.npc.NpcCoverStatus;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;

/**
 * Cop engages the target with weapons. Only entered after escalation or being attacked.
 */
public class CombatBehavior implements CopBehavior {

	/**
	 * How long one retreat lasts: a cop still hurt after this long in cover comes out and fights on for the rest of
	 * its COMBAT episode, so hurt cops cannot hide (and hold their spawn slot) until the wanted level ends.
	 */
	// ponytail: code constant, a Retreat.Max_Cover_Ticks key when owners want to tune it
	static final long MAX_COVER_MS = 10_000;

	private final double            combatRange;
	private final double            alertRange;
	private final DetainmentService detainmentService;
	private final RetreatSettings   retreat;
	private final LongSupplier      clock;
	/** When each cop's retreat of this COMBAT episode began; cleared on exit. Weak: a despawned cop drops out. */
	private final Map<CopNpc, Long> retreatStartedAt = new WeakHashMap<>();

	public CombatBehavior(double combatRange, double alertRange, DetainmentService detainmentService,
	                      RetreatSettings retreat) {
		this(combatRange, alertRange, detainmentService, retreat, System::currentTimeMillis);
	}

	CombatBehavior(double combatRange, double alertRange, DetainmentService detainmentService, RetreatSettings retreat,
	               LongSupplier clock) {
		this.combatRange       = combatRange;
		this.alertRange        = alertRange;
		this.detainmentService = detainmentService;
		this.retreat           = retreat;
		this.clock             = clock;
	}

	@Override
	public void tick(CopNpc cop) {
		LivingEntity target = resolveTarget(cop);
		if (target == null || !target.isValid() || target.isDead()) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		// Restrained check only applies to players
		if (target instanceof Player player && detainmentService.isRestrained(player)) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		// A ranged cop fires at anything it can see within its sight range, as in PURSUING: its firing band reaches
		// past Combat_Range, and a narrower gate would leave a stretch of the band where it holds without firing.
		// Melee tiers start a swing within Combat_Range; it lands only within the Keystone melee reach.
		double distance    = cop.distanceTo(target);
		double attackRange = cop.isRangedAttacker() ? alertRange : combatRange;

		if (distance <= attackRange && cop.canAttack() && cop.hasLineOfSight(target)) {
			if (target instanceof Player player) {
				cop.attack(player);
			} else {
				cop.attackEntity(target);
			}
		}

		// Badly hurt: break off to cover (the squad radios Fall_Back / In_Cover) and keep firing from there when seen,
		// for at most MAX_COVER_MS per COMBAT episode. No cover within the radius (open ground): keep fighting rather
		// than freeze on the spot.
		LivingEntity self = cop.getEntity();
		if (self != null && retreat.shouldRetreat(self.getHealth(), self.getMaxHealth()) && retreatTimeLeft(cop)) {
			// Fresh from PURSUING, stopNavigation cleared the Keystone squad: join it first so the retreat is radioed.
			if (cop.getCurrentSquad() == null) cop.pursue(target, cop.squadFor(target), alertRange);
			if (cop.takeCover(target, retreat.radius()) != NpcCoverStatus.FAILED) return;
		}

		// Keystone's squad pursuit: ranged cops work their post on the squad's fan while they see the target inside
		// their firing band, everyone else closes in, routes around obstacles or searches from the last-known position.
		// squadFor gives the group squad for the group's player and the group's shared squad against anyone else.
		cop.pursue(target, cop.squadFor(target), alertRange);
	}

	@Override
	public void onEnter(CopNpc cop) {
	}

	@Override
	public void onExit(CopNpc cop) {
		retreatStartedAt.remove(cop);
		cop.stopNavigation();
	}

	/** Starts the cop's retreat clock on first call; whether its retreat of this COMBAT episode still has time. */
	private boolean retreatTimeLeft(CopNpc cop) {
		long now = clock.getAsLong();
		return now - retreatStartedAt.computeIfAbsent(cop, c -> now) < MAX_COVER_MS;
	}

	private LivingEntity resolveTarget(CopNpc cop) {
		UUID id = cop.getTargetPlayerId();
		if (id != null) {
			return Bukkit.getPlayer(id);
		}
		LivingEntity entity = cop.getTargetEntity();
		return (entity != null && entity.isValid() && !entity.isDead()) ? entity : null;
	}
}
