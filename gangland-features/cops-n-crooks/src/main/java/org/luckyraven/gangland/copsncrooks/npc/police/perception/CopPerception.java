package org.luckyraven.gangland.copsncrooks.npc.police.perception;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

/**
 * The one place cop sight is decided (0.20.0 seam): lanes register {@link SightFactor}s (darkness, crowds, disguise
 * range) and {@link #addLockOnFilter lock-on filters} (disguise, police outfit) at startup; the cop behaviours ask here.
 * With nothing registered every answer equals the pre-0.20 one. Filters never stop a cop firing at a target it already
 * engages: only acquisition and the pursuit sight range go through here.
 */
public class CopPerception {

	/** Scales a cop's sight range against one player; registered factors multiply. */
	public interface SightFactor {

		double factor(CopNpc cop, Player target);
	}

	private final List<SightFactor>              factors = new CopyOnWriteArrayList<>();
	private final List<BiPredicate<CopNpc, Player>> filters = new CopyOnWriteArrayList<>();

	public void addSightFactor(SightFactor factor) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public void addLockOnFilter(BiPredicate<CopNpc, Player> filter) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** {@code base} times the product of every factor; a non-player target gets {@code base}. */
	public double rangeFor(CopNpc cop, LivingEntity target, double base) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** {@code cop.canSee(target, rangeFor(...))}. */
	public boolean sees(CopNpc cop, LivingEntity target, double base) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Every lock-on filter allows this cop to pick this player up; non-players are always allowed. */
	public boolean allowsLockOn(CopNpc cop, LivingEntity target) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** {@link #allowsLockOn} and {@link #sees}. */
	public boolean acquires(CopNpc cop, LivingEntity target, double base) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/**
	 * The sight range handed to {@code cop.pursue}: non-player {@code base}; an allowed player {@link #rangeFor};
	 * a filtered-out player stays visible only while {@code squad} has a fresh sighting, otherwise 0.
	 */
	public double pursuitRange(CopNpc cop, LivingEntity target, @Nullable NpcSquad squad, double base) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
