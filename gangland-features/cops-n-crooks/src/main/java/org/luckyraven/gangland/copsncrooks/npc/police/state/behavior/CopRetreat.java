package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.entity.LivingEntity;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.keystone.npc.NpcCoverStatus;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;

/**
 * The badly-hurt retreat of a fighting cop state: one instance per behaviour, reset in its {@code onExit}.
 */
final class CopRetreat {

	/**
	 * How long one retreat lasts: a cop still hurt after this long in cover comes out and fights on for the rest of
	 * its state episode, so hurt cops cannot hide (and hold their spawn slot) until the wanted level ends.
	 */
	// ponytail: code constant, a Retreat.Max_Cover_Ticks key when owners want to tune it
	static final long MAX_COVER_MS = 10_000;

	private final RetreatSettings   settings;
	private final LongSupplier      clock;
	/** When each cop's retreat of this episode began. Weak: a despawned cop drops out. */
	private final Map<CopNpc, Long> startedAt = new WeakHashMap<>();

	CopRetreat(RetreatSettings settings, LongSupplier clock) {
		this.settings = settings;
		this.clock    = clock;
	}

	/**
	 * Sends a badly hurt cop to cover (its squad radios Fall_Back / In_Cover; takeCover joins the squad itself).
	 *
	 * @return {@code true} while the cop is retreating, so the caller skips its own pursuit this tick; {@code false}
	 * when healthy, out of cover time, or with no cover in reach (open ground: fight on rather than freeze).
	 */
	boolean takeCover(CopNpc cop, LivingEntity target) {
		LivingEntity self = cop.getEntity();
		if (self == null || !settings.shouldRetreat(self.getHealth(), self.getMaxHealth())) return false;

		long now = clock.getAsLong();
		if (now - startedAt.computeIfAbsent(cop, c -> now) >= MAX_COVER_MS) return false;

		return cop.takeCover(target, settings.radius(), cop.squadFor(target)) != NpcCoverStatus.FAILED;
	}

	/** A new episode gets a fresh retreat. */
	void reset(CopNpc cop) {
		startedAt.remove(cop);
	}
}
