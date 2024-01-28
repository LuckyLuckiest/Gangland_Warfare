package me.luckyraven.feature.weapon.projectile.type;

import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.projectile.WeaponProjectile;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;

public class Bullet extends WeaponProjectile {

	public Bullet(LivingEntity shooter, Weapon weapon) {
		super(shooter, weapon, shooter.getEyeLocation(), shooter.getEyeLocation().getDirection());
	}

	@Override
	public void updatePosition(Projectile projectile) {

	}
}
