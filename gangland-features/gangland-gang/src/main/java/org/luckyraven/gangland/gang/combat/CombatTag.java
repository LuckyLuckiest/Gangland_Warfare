package org.luckyraven.gangland.gang.combat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

/**
 * The short "just hit by another gang" tag and the gang-fight flag. A hit tags both players, but only when both are
 * members of different gangs, so a gangless alt cannot lock a rival out of every safehouse. The fighting flag (used by
 * the fight module) is a plain set.
 */
public class CombatTag {

	private final Set<UUID>           fighting = ConcurrentHashMap.newKeySet();
	private final Map<UUID, Long>     until    = new ConcurrentHashMap<>();
	private final LongSupplier        clock;
	private final IntSupplier         tagSeconds;
	private final ToIntFunction<UUID> gangOf;

	/** A tag nobody can ever earn (no gang lookup); for tests that only need the seam. */
	public CombatTag() {
		this(System::currentTimeMillis, () -> 15, uuid -> -1);
	}

	/**
	 * @param clock the time source in milliseconds
	 * @param tagSeconds {@code Combat.Tag_Seconds}; 0 turns the tag off
	 * @param gangOf the player's gang id, {@code -1} when gangless
	 */
	public CombatTag(LongSupplier clock, IntSupplier tagSeconds, ToIntFunction<UUID> gangOf) {
		this.clock      = clock;
		this.tagSeconds = tagSeconds;
		this.gangOf     = gangOf;
	}

	public boolean isTagged(UUID player) {
		return !remaining(player).isZero();
	}

	/** Tags both players when they belong to two different gangs; a later hit restarts the clock. */
	public void tag(UUID attacker, UUID victim) {
		if (attacker == null || victim == null || attacker.equals(victim)) return;

		int seconds = tagSeconds.getAsInt();
		if (seconds <= 0) return;

		int attackerGang = gangOf.applyAsInt(attacker);
		int victimGang   = gangOf.applyAsInt(victim);
		if (attackerGang == -1 || victimGang == -1 || attackerGang == victimGang) return;

		long end = clock.getAsLong() + seconds * 1000L;
		until.merge(attacker, end, Math::max);
		until.merge(victim, end, Math::max);
	}

	public void setFighting(UUID player, boolean fighting) {
		if (fighting) this.fighting.add(player);
		else this.fighting.remove(player);
	}

	public boolean isFighting(UUID player) {
		return fighting.contains(player);
	}

	public Duration remaining(UUID player) {
		Long end = until.get(player);
		if (end == null) return Duration.ZERO;

		long left = end - clock.getAsLong();
		if (left <= 0) {
			until.remove(player, end);
			return Duration.ZERO;
		}
		return Duration.ofMillis(left);
	}
}
