package me.luckyraven.weapon.types.incendiary;

import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.util.utilities.ParticleUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.dto.IncendiaryData;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

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
		double   angle = Math.toRadians(data.getConeAngle() / 2.0);

		// spray particles along the look direction using ParticleUtil
		ParticleUtil.spawnFireSpray(eye, dir, range);

		// ignite entities within the cone
		for (Entity entity : player.getNearbyEntities(range, range, range)) {
			if (!(entity instanceof LivingEntity target)) continue;
			if (entity.equals(player)) continue;

			Vector toTarget = target.getLocation().toVector().subtract(eye.toVector()).normalize();

			if (dir.dot(toTarget) < Math.cos(angle)) continue;
			if (target.getLocation().distanceSquared(eye) > range * range) continue;

			target.setFireTicks(data.getFireDuration());
		}
	}

}
