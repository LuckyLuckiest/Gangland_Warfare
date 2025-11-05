package me.luckyraven.feature.weapon.projectile.type;

import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.projectile.WeaponProjectile;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Snowball;
import org.bukkit.plugin.java.JavaPlugin;

public class Bullet extends WeaponProjectile<Snowball> {

	public Bullet(JavaPlugin plugin, LivingEntity shooter, Weapon weapon) {
		super(plugin, shooter, weapon, shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(),
			  Snowball.class);
	}

}
