package me.luckyraven.weapon.types.incendiary;

import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.util.utilities.ParticleUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.dto.IncendiaryData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class IncendiaryAction {

	/**
	 * Starts continuous fire spray. Toggles off if already active.
	 */
	public static void start(JavaPlugin plugin, Player player, Weapon weapon, Map<UUID, RepeatingTimer> activeTasks) {
		UUID weaponUuid = weapon.getUuid();

		if (activeTasks.containsKey(weaponUuid)) {
			stop(weapon, activeTasks);
			return;
		}

		IncendiaryData data = weapon.getIncendiaryData();
		if (data == null) return;

		int[] fuel = {data.getFuelCapacity()};

		RepeatingTimer timer = new RepeatingTimer(plugin, data.getTickRate(), time -> {
			if (fuel[0] <= 0) {
				stop(weapon, activeTasks);
				time.stop();
				return;
			}

			fuel[0] = Math.max(0, fuel[0] - data.getFuelConsumeRate());
			sprayFire(player, data);
		});

		timer.start(false);
		activeTasks.put(weaponUuid, timer);
	}

	public static void stop(Weapon weapon, Map<UUID, RepeatingTimer> activeTasks) {
		RepeatingTimer timer = activeTasks.remove(weapon.getUuid());
		if (timer != null) timer.stop();
	}

	private static void sprayFire(Player player, IncendiaryData data) {
		Location eye   = player.getEyeLocation();
		Vector   dir   = eye.getDirection().normalize();
		double   range = data.getRange();
		World    world = player.getWorld();

		// muzzle position: in front, shifted to the right hand side, slightly below eye
		Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
		if (right.lengthSquared() < 0.001) right = new Vector(1, 0, 0);
		right.normalize();
		Location muzzle = eye.clone()
							 .add(dir.clone().multiply(0.5))
							 .add(right.multiply(0.25))
							 .add(0, -0.15, 0);

		// flame particles from muzzle in a realistic cone with upward drift
		ParticleUtil.spawnFlameCone(muzzle, dir, range, data.getConeAngle());

		// ray-trace multiple directions within the cone to find entities accurately
		double            halfAngle = Math.toRadians(data.getConeAngle() / 2.0);
		int               rays      = Math.max(4, (int) (data.getConeAngle() / 8));
		ThreadLocalRandom rng       = ThreadLocalRandom.current();

		Vector perp1 = dir.clone().crossProduct(new Vector(0, 1, 0));
		if (perp1.lengthSquared() < 0.001) perp1 = dir.clone().crossProduct(new Vector(1, 0, 0));
		perp1.normalize();
		Vector perp2 = dir.clone().crossProduct(perp1).normalize();

		Set<LivingEntity> hit = new HashSet<>();
		for (int r = 0; r < rays; r++) {
			double theta = rng.nextDouble() * halfAngle;
			double phi   = rng.nextDouble() * 2 * Math.PI;
			Vector rayDir = dir.clone()
							   .add(perp1.clone().multiply(Math.sin(theta) * Math.cos(phi)))
							   .add(perp2.clone().multiply(Math.sin(theta) * Math.sin(phi)))
							   .normalize();

			RayTraceResult result = world.rayTraceEntities(muzzle, rayDir, range, 0.3,
														   e -> e instanceof LivingEntity && !e.equals(player));
			if (result != null && result.getHitEntity() instanceof LivingEntity target) {
				hit.add(target);
			}
		}

		for (LivingEntity target : hit) {
			target.setFireTicks(data.getFireDuration());
		}
	}

}
