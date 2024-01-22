package me.luckyraven.feature.weapon.projectile;

import me.luckyraven.feature.weapon.Weapon;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Snowball;
import org.bukkit.util.Vector;

public abstract class WeaponProjectile extends WProjectile {

	private final Weapon weapon;

	public WeaponProjectile(LivingEntity shooter, Weapon weapon, Location location, Vector velocity) {
		super(shooter, location, velocity);

		this.weapon = weapon;
	}

	@Override
	public void launchProjectile() {
		// Get the starting position


		// Get the eye location
		Location eyeLocation = getShooter().getEyeLocation();
		Vector   velocity    = getVelocity();
		// calculate the spawn location based on the view direction
		Location spawnLocation = eyeLocation.clone().add(eyeLocation.getDirection());

		// spawn the projectile
		Snowball projectile = getShooter().getWorld().spawn(spawnLocation, Snowball.class);

		projectile.setSilent(true);
		projectile.setGravity(false);
		projectile.setShooter(getShooter());

		// set the velocity according to the modified values
		projectile.setVelocity(velocity.multiply(getSpeed()));

		// apply spread

	}

	@Override
	public double getSpeed() {
		return weapon.getProjectileSpeed();
	}
}
