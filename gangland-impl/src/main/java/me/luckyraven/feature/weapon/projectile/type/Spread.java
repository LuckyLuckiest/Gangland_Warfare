package me.luckyraven.feature.weapon.projectile.type;

import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.projectile.WeaponProjectile;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Snowball;
import org.bukkit.plugin.java.JavaPlugin;

public class Spread extends WeaponProjectile<Snowball> {

	private final int pelletsCount;

	public Spread(JavaPlugin plugin, LivingEntity shooter, Weapon weapon) {
		this(plugin, shooter, weapon, 8);
	}

	public Spread(JavaPlugin plugin, LivingEntity shooter, Weapon weapon, int pelletsCount) {
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
