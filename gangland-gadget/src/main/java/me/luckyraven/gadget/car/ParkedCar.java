package me.luckyraven.gadget.car;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Persistent data record for a parked car stored in the database. Contains only serialisable fields — no live entity
 * references. The {@code dbId} is a stable UUID string assigned once at placement time and used as the database primary
 * key across server restarts.
 */
@Getter
public class ParkedCar {

	private final String dbId;
	private final String carId;
	private final String world;
	private final double x;
	private final double y;
	private final double z;
	private final float  yaw;
	@Nullable
	private final UUID   placerUUID;
	@Setter
	private       int    fuel;
	@Setter
	private       int    durability;

	public ParkedCar(String dbId, String carId, String world,
	                 double x, double y, double z, float yaw,
	                 int fuel, int durability, @Nullable UUID placerUUID) {
		this.dbId       = dbId;
		this.carId      = carId;
		this.world      = world;
		this.x          = x;
		this.y          = y;
		this.z          = z;
		this.yaw        = yaw;
		this.fuel       = fuel;
		this.durability = durability;
		this.placerUUID = placerUUID;
	}
}
