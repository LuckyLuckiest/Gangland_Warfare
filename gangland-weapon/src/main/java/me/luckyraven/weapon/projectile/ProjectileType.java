package me.luckyraven.weapon.projectile;

import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.projectile.type.Bullet;
import me.luckyraven.weapon.projectile.type.Flare;
import me.luckyraven.weapon.projectile.type.Rocket;
import me.luckyraven.weapon.projectile.type.Spread;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

public enum ProjectileType {

	BULLET,
	SPREAD,
	FLARE,
	ROCKET;

	public static ProjectileType getType(String type) {
		return switch (type.toLowerCase()) {
			case "flare" -> FLARE;
			case "spread" -> SPREAD;
			case "rocket" -> ROCKET;
			default -> BULLET;
		};
	}

	public WeaponProjectile<?> createInstance(JavaPlugin plugin, LivingEntity shooter, Weapon weapon) {
		return switch (weapon.getProjectileType()) {
			case BULLET -> new Bullet(plugin, shooter, weapon);
			case SPREAD -> new Spread(plugin, shooter, weapon);
			case FLARE -> new Flare(plugin, shooter, weapon);
			case ROCKET -> new Rocket(plugin, shooter, weapon);
		};
	}


}
