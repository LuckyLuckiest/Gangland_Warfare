package me.luckyraven.weapon.projectile.type;

import me.luckyraven.weapon.projectile.WeaponProjectile;
import me.luckyraven.weapon.types.gun.GunWeapon;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

public class Flare extends WeaponProjectile<Firework> {

	public Flare(JavaPlugin plugin, LivingEntity shooter, GunWeapon weapon) {
		super(plugin, shooter, weapon, shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(),
		      Firework.class);
	}

}
