package me.luckyraven.weapon.projectile.type;

import me.luckyraven.weapon.projectile.WeaponProjectile;
import me.luckyraven.weapon.types.gun.GunWeapon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Snowball;
import org.bukkit.plugin.java.JavaPlugin;

public class Spread extends WeaponProjectile<Snowball> {

	private final int pelletsCount;

	public Spread(JavaPlugin plugin, LivingEntity shooter, GunWeapon weapon) {
		this(plugin, shooter, weapon, 8);
	}

	public Spread(JavaPlugin plugin, LivingEntity shooter, GunWeapon weapon, int pelletsCount) {
		super(plugin, shooter, weapon, shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(),
		      Snowball.class);
		this.pelletsCount = pelletsCount;
	}


	@Override
	public void launchProjectile() {
		for (int i = 0; i < pelletsCount; i++) {
			super.launchProjectile();
		}
	}
}
