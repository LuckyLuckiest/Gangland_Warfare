package me.luckyraven.weapon.projectile.type;

import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.projectile.WeaponProjectile;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

public class Flare extends WeaponProjectile<Firework> {

	public Flare(JavaPlugin plugin, LivingEntity shooter, Weapon weapon) {
		super(plugin, shooter, weapon, shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(),
			  Firework.class);
	}

}
