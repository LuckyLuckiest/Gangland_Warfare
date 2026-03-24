package me.luckyraven.weapon.types.throwable;

import me.luckyraven.util.timer.CountdownTimer;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.dto.ThrowableData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

public class ThrowableAction {

	public static void activate(JavaPlugin plugin, Player player, Weapon weapon) {
		ThrowableData data = weapon.getThrowableData();
		if (data == null) return;

		// consume weapon item if configured
		if (weapon.getWeaponConsumedOnShot() > 0) {
			weapon.removeWeapon(player, player.getInventory().getHeldItemSlot());
		}

		// spawn entity from eye location with forward velocity
		Location eyeLoc = player.getEyeLocation();
		Entity   entity = player.getWorld().spawn(eyeLoc, resolveEntityClass(data.getEntityType()));
		entity.setVelocity(eyeLoc.getDirection().multiply(1.5));

		// schedule detonation after fuse (1 tick period, fuseTime ticks countdown)
		new CountdownTimer(plugin, 0L, 1L, data.getFuseTime(), null, null, timer -> {
			Location detonation = entity.getLocation();
			entity.remove();

			World world = detonation.getWorld();
			if (world == null) return;

			world.createExplosion(detonation.getX(), detonation.getY(), detonation.getZ(),
								  (float) data.getExplosionRadius(), data.getFireTicks() > 0, false, player);

			double radiusSq = data.getExplosionRadius() * data.getExplosionRadius();
			for (Entity nearby : world.getNearbyEntities(detonation, data.getExplosionRadius(),
														 data.getExplosionRadius(), data.getExplosionRadius())) {
				if (!(nearby instanceof LivingEntity target)) continue;
				if (nearby.equals(player)) continue;
				if (nearby.getLocation().distanceSquared(detonation) > radiusSq) continue;
				if (data.getExplosionDamage() > 0) target.damage(data.getExplosionDamage(), player);
				if (data.getFireTicks() > 0) target.setFireTicks(data.getFireTicks());
			}
		}).start(false);
	}

	private static Class<? extends Entity> resolveEntityClass(String entityType) {
		return switch (entityType.toUpperCase()) {
			case "EGG" -> Egg.class;
			case "ENDER_PEARL" -> EnderPearl.class;
			default -> Snowball.class;
		};
	}

}
