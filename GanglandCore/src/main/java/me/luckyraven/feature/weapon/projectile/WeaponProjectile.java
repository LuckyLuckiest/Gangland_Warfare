package me.luckyraven.feature.weapon.projectile;

import me.luckyraven.feature.weapon.Weapon;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Snowball;
import org.bukkit.util.Vector;

import java.util.Random;

public abstract class WeaponProjectile extends WProjectile {

	private final Weapon weapon;
	private final Random random;

	public WeaponProjectile(LivingEntity shooter, Weapon weapon, Location location, Vector velocity) {
		super(shooter, location, velocity);

		this.weapon = weapon;
		this.random = new Random();
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

		// apply spread
		Vector spread = applySpread(velocity, weapon.getSpreadStart());

		// set the velocity according to the modified values
		projectile.setVelocity(spread.multiply(getSpeed()));
	}

	@Override
	public double getSpeed() {
		return weapon.getProjectileSpeed();
	}

	private Vector applySpread(Vector originalVector, double spreadFactor) {
		double offsetX = (random.nextDouble() - 0.5) * spreadFactor;
		double offsetY = (random.nextDouble() - 0.5) * spreadFactor;
		double offsetZ = (random.nextDouble() - 0.5) * spreadFactor;

		return originalVector.add(new Vector(offsetX, offsetY, offsetZ)).normalize();
	}
}
