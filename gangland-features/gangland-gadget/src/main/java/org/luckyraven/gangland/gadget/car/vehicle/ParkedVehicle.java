package org.luckyraven.gangland.gadget.car.vehicle;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.ExhaustSide;
import org.luckyraven.gangland.gadget.car.vehicle.entity.VehicleEntity;

import java.util.UUID;

/**
 * Represents a car that has been placed in the world but is not currently being driven. Data is preserved through
 * server reloads via {@link org.bukkit.persistence.PersistentDataContainer} stored on the underlying entity.
 */
@Getter
public class ParkedVehicle {

	private final VehicleEntity entity;
	private final Car           car;
	private final UUID          placerUUID;
	@Nullable
	private final ExhaustSide   exhaustSide;
	private final int           maxFuel;
	private       int           fuel;
	private       int           durability;

	public ParkedVehicle(VehicleEntity entity, Car car, UUID placerUUID, int fuel, int maxFuel, int durability,
	                     @Nullable ExhaustSide exhaustSide) {
		this.entity      = entity;
		this.car         = car;
		this.placerUUID  = placerUUID;
		this.fuel        = fuel;
		this.maxFuel     = maxFuel;
		this.durability  = durability;
		this.exhaustSide = exhaustSide;
	}

	public void damage(int amount) {
		durability = Math.max(0, durability - amount);
	}

	public void addFuel(int amount) {
		fuel = Math.max(0, fuel + amount);
	}

	public boolean isDestroyed() {
		return durability <= 0;
	}
}
