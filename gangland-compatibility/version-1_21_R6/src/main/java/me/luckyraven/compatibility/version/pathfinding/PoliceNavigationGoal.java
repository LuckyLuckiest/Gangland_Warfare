package me.luckyraven.compatibility.version.pathfinding;

import lombok.Getter;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Custom pathfinder goal for police navigation control. Uses Mojang-mapped names (requires remapped spigot
 * dependency).
 */
public class PoliceNavigationGoal extends Goal {

	private final PathfinderMob mob;

	@Getter
	private Vec3   targetPos;
	private double speed;
	private int    repathTicks;

	public PoliceNavigationGoal(PathfinderMob mob) {
		this.mob         = mob;
		this.targetPos   = null;
		this.speed       = 1.0;
		this.repathTicks = 0;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	public void setTarget(double x, double y, double z, double speed) {
		this.targetPos   = new Vec3(x, y, z);
		this.speed       = speed;
		this.repathTicks = 20; // Immediate repath
	}

	public void clearTarget() {
		this.targetPos = null;
		mob.getNavigation().stop();
	}

	public boolean hasTarget() {
		return targetPos != null;
	}

	@Override
	public boolean canUse() {
		return targetPos != null;
	}

	@Override
	public boolean canContinueToUse() {
		if (targetPos == null) return false;
		if (mob.getNavigation().isDone()) {
			double distSq = mob.distanceToSqr(targetPos);
			return distSq > 2.25;
		}
		return true;
	}

	@Override
	public void start() {
		repathTicks = 0;
		updatePath();
	}

	@Override
	public void stop() {
		mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (targetPos == null) return;

		// Look toward target
		mob.getLookControl().setLookAt(targetPos.x, targetPos.y + 1.0, targetPos.z);

		// Recalculate path periodically
		if (++repathTicks >= 10) {
			updatePath();
			repathTicks = 0;
		}
	}

	private void updatePath() {
		if (targetPos == null) return;
		mob.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, speed);
	}

}
