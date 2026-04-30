package org.luckyraven.gangland.gadget.car.vehicle.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.luckyraven.gangland.gadget.car.Car;

import java.util.UUID;

/**
 * Vehicle implementation backed by a {@link Minecart} entity. Movement is applied via velocity manipulation rather than
 * teleportation. Does not require rails.
 */
public class MinecartVehicle implements VehicleEntity {

	private final Car car;

	private Minecart minecart;

	public MinecartVehicle(Car car) {
		this.car = car;
	}

	/**
	 * Wraps an already-existing minecart entity (used when re-loading parked vehicles after restart).
	 */
	public MinecartVehicle(Car car, Minecart existingMinecart) {
		this.car      = car;
		this.minecart = existingMinecart;
	}

	@Override
	public void spawn(Location location) {
		World world = location.getWorld();
		if (world == null) return;

		minecart = world.spawn(location.clone(), Minecart.class, cart -> {
			// Use a high internal max-speed ceiling so our manually applied velocity is never capped.
			// We manage speed entirely via setVelocity each tick.
			cart.setMaxSpeed(10.0);
			cart.setSlowWhenEmpty(false);
		});
	}

	@Override
	public void mount(Player player) {
		if (minecart != null && !minecart.isDead()) {
			minecart.addPassenger(player);
		}
	}

	@Override
	public void despawn() {
		if (minecart != null && !minecart.isDead()) {
			minecart.eject();
			minecart.remove();
		}
	}

	@Override
	public void updateMovement(double speed, float yaw) {
		if (minecart == null || minecart.isDead()) return;

		double radians = Math.toRadians(yaw);
		double dx      = -Math.sin(radians) * speed;
		double dz      = Math.cos(radians) * speed;

		double y = minecart.getVelocity().getY();
		if (Math.abs(speed) > 0.001 && canStepUp(dx, dz)) {
			y = 0.42;
		}

		// Always set velocity to override minecart physics drag each tick.
		// Negative speed naturally produces reverse movement along the same yaw axis.
		minecart.setVelocity(new Vector(dx, y, dz));
	}

	@Override
	public void setFacing(float yaw) {
		if (minecart == null || minecart.isDead()) return;
		minecart.setRotation(yaw, 0);
	}

	@Override
	public boolean isAlive() {
		return minecart != null && !minecart.isDead() && minecart.isValid();
	}

	@Override
	public Location getLocation() {
		return minecart != null ? minecart.getLocation() : null;
	}

	@Override
	public Entity getBukkitEntity() {
		return minecart;
	}

	@Override
	public UUID getEntityUUID() {
		return minecart != null ? minecart.getUniqueId() : null;
	}

	@Override
	public void wobble(JavaPlugin plugin) {
		if (minecart == null || minecart.isDead() || !minecart.isEmpty()) return;

		Minecart cart  = minecart;
		Location start = cart.getLocation().clone();
		// Perpendicular (right) vector relative to the cart's facing direction
		Vector facing = start.getDirection().setY(0).normalize();
		Vector right  = new Vector(-facing.getZ(), 0, facing.getX());

		new BukkitRunnable() {
			int ticks = 0;

			@Override
			public void run() {
				if (cart.isDead() || ticks >= 12) {
					cart.setVelocity(new Vector(0, 0, 0));
					cancel();
					return;
				}

				// Alternating direction each tick with decaying amplitude — set, not add
				double amplitude = 0.12 * (1.0 - (double) ticks / 12.0);
				double side      = ticks % 2 == 0 ? amplitude : -amplitude;

				cart.setVelocity(right.clone().multiply(side));
				ticks++;
			}
		}.runTaskTimer(plugin, 0L, 1L);
	}

	/**
	 * Probes the block in the cart's travel direction and returns {@code true} when its top surface sits above the
	 * cart's current Y by a climbable margin (≤ 0.5 blocks) with passable clearance above. Handles slabs, closed
	 * trapdoors, and full blocks — so the cart can also step from a slab up onto an adjacent full block.
	 */
	private boolean canStepUp(double dx, double dz) {
		BlockFace face = faceFor(dx, dz);
		if (face == null) return false;

		// Already rising from an earlier bump — skip, otherwise the cart stacks Y velocity and flies / bobs.
		if (minecart.getVelocity().getY() > 0.05) return false;

		Block  probe = minecart.getLocation().getBlock().getRelative(face);
		Double topY  = stepTopY(probe);
		if (topY == null) return false;

		// Obstacle's top must be above the cart by a climbable margin — rules out "cart already at that height"
		// (bobbing) and "obstacle too tall" (climbing full blocks from flat ground).
		double diff = topY - minecart.getLocation().getY();
		if (diff <= 0.05 || diff > 0.6) return false;

		return !probe.getRelative(BlockFace.UP).getType().isSolid();
	}

	/**
	 * Returns the block's top-surface Y for step-up purposes, or {@code null} if the block is passable /
	 * non-climbable.
	 */
	private Double stepTopY(Block block) {
		BlockData data = block.getBlockData();
		if (data instanceof Slab slab) {
			return switch (slab.getType()) {
				case BOTTOM -> block.getY() + 0.5;
				case TOP, DOUBLE -> block.getY() + 1.0;
			};
		}
		if (data instanceof TrapDoor trapDoor) {
			if (trapDoor.isOpen()) return null;
			return block.getY() + (trapDoor.getHalf() == Bisected.Half.BOTTOM ? 0.1875 : 1.0);
		}
		if (block.getType().isSolid()) {
			return (double) block.getY() + 1.0;
		}
		return null;
	}

	/**
	 * Picks the cardinal face matching the dominant axis of travel. Returns {@code null} when motion is effectively
	 * zero so callers skip the probe.
	 */
	private BlockFace faceFor(double dx, double dz) {
		if (Math.abs(dx) < 1e-4 && Math.abs(dz) < 1e-4) return null;
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx > 0 ? BlockFace.EAST : BlockFace.WEST;
		}
		return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
	}
}
