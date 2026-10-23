package org.luckyraven.gangland.civilians.witness;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;

import java.util.function.Predicate;

/**
 * {@link WitnessService.Aim}: the criminal is armed ({@code armed}), within range blocks of the caller and looking at
 * the caller to within angleDegrees. Pure geometry; the armed test is injected so this class never names Bartizan.
 *
 * @since 0.20.0
 */
public final class WitnessAim implements WitnessService.Aim {

	private final Predicate<Player> armed;

	public WitnessAim(Predicate<Player> armed) {
		this.armed = armed;
	}

	@Override
	public boolean aimsAt(Player criminal, CivilianNpc caller, double range, double angleDegrees) {
		LivingEntity entity = caller.getEntity();
		if (entity == null) return false;
		if (!armed.test(criminal)) return false;

		World criminalWorld = criminal.getWorld();
		if (criminalWorld == null || !criminalWorld.equals(entity.getWorld())) return false;

		Location eye    = criminal.getEyeLocation();
		Vector   toward = entity.getEyeLocation().toVector().subtract(eye.toVector());
		if (toward.length() > range) return false;
		if (toward.length() < 0.0001) return true;

		return Math.toDegrees(eye.getDirection().angle(toward)) <= angleDegrees;
	}
}
