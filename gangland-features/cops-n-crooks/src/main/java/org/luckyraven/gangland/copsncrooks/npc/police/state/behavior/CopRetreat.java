package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
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
	 * Sends a badly hurt cop to cover (its squad radios Fall_Back / In_Cover; takeCover joins the squad itself), by
	 * its role's {@code Retreat} when it has one. While its group falls back (its Commander went down) every cop takes
	 * cover, hurt or not, without spending its own cover time; {@code Retreat.Enabled: false} turns that off too.
	 * Keystone's cover primitive fires {@code FALL_BACK} for each of them, so healthy cops may radio the Fall_Back
	 * line right after Commander_Down.
	 *
	 * @return {@code true} while the cop is retreating, so the caller skips its own pursuit this tick; {@code false}
	 * when healthy, out of cover time, or with no cover in reach (open ground: fight on rather than freeze).
	 */
	boolean takeCover(CopNpc cop, LivingEntity target) {
		cop.setMovingToCover(false);   // set again by cover() while the walk is on
		LivingEntity self = cop.getEntity();
		if (self == null) return false;

		CopRole         role     = cop.getRole();
		RetreatSettings retreat  = role != null && role.retreat() != null ? role.retreat() : settings;
		long            now      = clock.getAsLong();
		CopGroup        group    = cop.getGroup();
		if (group != null && group.isFallingBack(now)) return retreat.enabled() && cover(cop, target, retreat);

		if (!retreat.shouldRetreat(self.getHealth(), self.getMaxHealth())) return false;
		if (now - startedAt.computeIfAbsent(cop, c -> now) >= MAX_COVER_MS) return false;

		return cover(cop, target, retreat);
	}

	private static boolean cover(CopNpc cop, LivingEntity target, RetreatSettings retreat) {
		NpcCoverStatus status = cop.takeCover(target, retreat.radius(), cop.squadFor(target));
		cop.setMovingToCover(status == NpcCoverStatus.MOVING);
		return status != NpcCoverStatus.FAILED;
	}

	/** A new episode gets a fresh retreat. */
	void reset(CopNpc cop) {
		startedAt.remove(cop);
		cop.setMovingToCover(false);
	}
}
