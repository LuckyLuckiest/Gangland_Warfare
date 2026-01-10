package me.luckyraven.compatibility.version.pathfinding;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class PoliceNavGoal extends Goal {

	private final PathfinderMob mob;
	private       Vec3          targetPos;
	private       double        speed;
	private       int           repathCounter;

	PoliceNavGoal(PathfinderMob mob) {
		this.mob           = mob;
		this.targetPos     = null;
		this.speed         = 1.0;
		this.repathCounter = 0;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return targetPos != null;
	}

	@Override
	public boolean canContinueToUse() {
		if (targetPos == null) return false;
		// Stop if close enough
		double distSq = mob.distanceToSqr(targetPos);
		return distSq > 2.25; // 1.5 blocks squared
	}

	@Override
	public void start() {
		repath();
	}

	@Override
	public void stop() {
		mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (targetPos == null) return;

		// Look at target
		mob.getLookControl().setLookAt(targetPos.x, targetPos.y + 1.0, targetPos.z);

		// Repath every 10 ticks for moving targets
		if (++repathCounter >= 10) {
			repathCounter = 0;
			repath();
		}
	}

	void setTarget(double x, double y, double z, double speed) {
		this.targetPos     = new Vec3(x, y, z);
		this.speed         = speed;
		this.repathCounter = 0;
	}

	void clearTarget() {
		this.targetPos = null;
		mob.getNavigation().stop();
	}

	boolean hasTarget() {
		return targetPos != null;
	}

	Vec3 getTargetPos() {
		return targetPos;
	}

	private void repath() {
		if (targetPos == null) return;
		mob.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, speed);
	}

}
