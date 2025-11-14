package me.luckyraven.weapon.projectile;

import com.google.common.base.Preconditions;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public abstract class WProjectile {

	@Getter
	private final LivingEntity shooter;
	@Getter
	private final World        world;

	private Vector lastLocation;
	private Vector location;
	private Vector velocity;
	private int    aliveTicks;
	private double distanceTravelled;

	public WProjectile(LivingEntity shooter, Location location, Vector velocity) {
		this.shooter      = shooter;
		this.world        = location.getWorld();
		this.location     = location.toVector();
		this.lastLocation = location.toVector();
		this.velocity     = velocity;
	}

	public abstract void launchProjectile();

	public abstract double getSpeed();

	public double getGravity() {
		return 0.05;
	}

	public double getDomainDrag() {
		if (getCurrentBlock().isLiquid()) return 0.96;
		else if (world.isThundering() || world.hasStorm()) return 0.98;
		else return 0.99;
	}

	public Block getCurrentBlock() {
		return world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}

	public int getMaxAliveTicks() {
		return 600;
	}

	public Vector getLastLocation() {
		return lastLocation.clone();
	}

	public Vector getLocation() {
		return location.clone();
	}

	public void setLocation(@NotNull Vector location) {
		Preconditions.checkNotNull(location, "Location can't be null!");

		this.lastLocation = this.location.clone();
		this.location     = location;
	}

	public Vector getVelocity() {
		return velocity.clone();
	}

	public void setVelocity(@NotNull Vector velocity) {
		Preconditions.checkNotNull(velocity, "Velocity can't be null!");
		this.velocity = velocity;
	}

	public double getX() {
		return location.getX();
	}

	public double getY() {
		return location.getY();
	}

	public double getZ() {
		return location.getZ();
	}

	public void addDistanceTravelled(double amount) {
		distanceTravelled += amount;
	}

}
