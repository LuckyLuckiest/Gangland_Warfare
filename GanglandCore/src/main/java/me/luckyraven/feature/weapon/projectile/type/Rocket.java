package me.luckyraven.feature.weapon.projectile.type;

import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.projectile.WeaponProjectile;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.java.JavaPlugin;

public class Rocket extends WeaponProjectile<Fireball> {

	public Rocket(JavaPlugin plugin, LivingEntity shooter, Weapon weapon) {
		super(plugin, shooter, weapon, shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(),
			  Fireball.class);
	}

	@Override
	public void updatePosition(Projectile projectile) {

	}
}
