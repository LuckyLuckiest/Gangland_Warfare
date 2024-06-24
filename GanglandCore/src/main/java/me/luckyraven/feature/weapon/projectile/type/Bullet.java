package me.luckyraven.feature.weapon.projectile.type;

import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.projectile.WeaponProjectile;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;

public class Bullet extends WeaponProjectile<Snowball> {

	public Bullet(LivingEntity shooter, Weapon weapon) {
		super(shooter, weapon, shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(), Snowball.class);
	}

	@Override
	public void updatePosition(Projectile projectile) {

	}
}
