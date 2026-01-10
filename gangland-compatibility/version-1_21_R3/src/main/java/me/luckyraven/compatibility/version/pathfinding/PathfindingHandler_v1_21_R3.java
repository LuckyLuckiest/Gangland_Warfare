package me.luckyraven.compatibility.version.pathfinding;

import me.luckyraven.compatibility.pathfinding.PathfindingHandler;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R3.entity.CraftMob;
import org.bukkit.entity.Mob;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PathfindingHandler_v1_21_R3 implements PathfindingHandler {

	private final Map<UUID, PoliceNavGoal> navigationGoals = new ConcurrentHashMap<>();

	@Override
	public boolean navigateTo(Mob mob, Location target, double speed) {
		if (mob == null || target == null || mob.isDead()) return false;
		if (target.getWorld() == null || !target.getWorld().equals(mob.getWorld())) return false;
		if (!(mob instanceof CraftMob craftMob)) return false;

		net.minecraft.world.entity.Mob nmsMob = craftMob.getHandle();

		// Check if it's a PathfinderMob (most ground mobs)
		if (nmsMob instanceof PathfinderMob pathfinderMob) {
			PoliceNavGoal goal = getOrCreateGoal(pathfinderMob, mob.getUniqueId());
			goal.setTarget(target.getX(), target.getY(), target.getZ(), speed);
			return true;
		}

		// Fallback for non-PathfinderMob
		PathNavigation navigation = nmsMob.getNavigation();
		return navigation.moveTo(target.getX(), target.getY(), target.getZ(), speed);
	}

	@Override
	public void stopNavigation(Mob mob) {
		if (mob == null || mob.isDead()) return;

		PoliceNavGoal goal = navigationGoals.get(mob.getUniqueId());
		if (goal != null) {
			goal.clearTarget();
		}

		if (!(mob instanceof CraftMob craftMob)) return;

		craftMob.getHandle().getNavigation().stop();
	}

	@Override
	public boolean isNavigating(Mob mob) {
		if (mob == null || mob.isDead()) return false;

		PoliceNavGoal goal = navigationGoals.get(mob.getUniqueId());

		if (goal != null) {
			return goal.hasTarget();
		}

		if (!(mob instanceof CraftMob craftMob)) return false;

		return craftMob.getHandle().getNavigation().isInProgress();
	}

	@Override
	public Location getTargetLocation(Mob mob) {
		if (mob == null || mob.isDead()) return null;

		PoliceNavGoal goal = navigationGoals.get(mob.getUniqueId());

		if (goal != null && goal.hasTarget()) {
			Vec3 pos = goal.getTargetPos();
			return new Location(mob.getWorld(), pos.x, pos.y, pos.z);
		}

		if (!(mob instanceof CraftMob craftMob)) return null;

		Path path = craftMob.getHandle().getNavigation().getPath();
		if (!(path != null && !path.isDone())) return null;

		var t = path.getTarget();
		return new Location(mob.getWorld(), t.getX(), t.getY(), t.getZ());
	}

	@Override
	public void clearAIGoals(Mob mob) {
		if (mob == null || mob.isDead()) return;
		if (!(mob instanceof CraftMob craftMob)) return;

		net.minecraft.world.entity.Mob nmsMob = craftMob.getHandle();

		if (!(nmsMob instanceof PathfinderMob pathfinderMob)) return;

		clearGoals(pathfinderMob.goalSelector);
		clearGoals(pathfinderMob.targetSelector);
	}

	@Override
	public void setAIEnabled(Mob mob, boolean enabled) {
		if (mob == null || mob.isDead()) return;
		if (!(mob instanceof CraftMob craftMob)) return;

		craftMob.getHandle().setNoAi(!enabled);
	}

	@Override
	public void cleanup(UUID entityId) {
		navigationGoals.remove(entityId);
	}

	private PoliceNavGoal getOrCreateGoal(PathfinderMob mob, UUID entityId) {
		return navigationGoals.computeIfAbsent(entityId, id -> {
			PoliceNavGoal goal = new PoliceNavGoal(mob);
			mob.goalSelector.addGoal(0, goal); // Priority 0 = highest
			return goal;
		});
	}

	private void clearGoals(GoalSelector selector) {
		Set<WrappedGoal> goals    = selector.getAvailableGoals();
		Set<Goal>        toRemove = new HashSet<>();

		for (WrappedGoal wg : goals) {
			// Keep our custom navigation goal
			if (wg.getGoal() instanceof PoliceNavGoal) continue;

			toRemove.add(wg.getGoal());
		}

		for (Goal g : toRemove) {
			selector.removeGoal(g);
		}
	}

}