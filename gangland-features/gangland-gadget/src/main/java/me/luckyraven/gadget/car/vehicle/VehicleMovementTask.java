package me.luckyraven.gadget.car.vehicle;

import me.luckyraven.gadget.car.Car;
import me.luckyraven.gadget.car.CarService;
import me.luckyraven.gadget.config.GadgetPhysicsConfig;
import me.luckyraven.item.fuel.FuelBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tick-based movement handler for an active car. Runs every server tick (50 ms) while a player is driving.
 *
 * <p>Movement is controlled by WASD keys. Input is written into {@link VehicleSession} by
 * {@link VehicleInputInterceptor} (Netty IO thread) each time the client sends a steering packet, and read here on the
 * main thread. The vehicle maintains its own yaw which turns with A/D. W accelerates forward, S reverses, sneak brakes
 * hard. Fuel is consumed each tick if fuel is enabled.
 */
public class VehicleMovementTask extends BukkitRunnable {

	private final VehicleSession      session;
	private final CarService          carService;
	private final GadgetPhysicsConfig physicsConfig;

	private double currentSpeed;
	private float  currentYaw;

	public VehicleMovementTask(VehicleSession session, CarService carService, GadgetPhysicsConfig physicsConfig) {
		this.session       = session;
		this.carService    = carService;
		this.physicsConfig = physicsConfig;
		this.currentSpeed  = 0;
		this.currentYaw    = session.getDriver().getLocation().getYaw();
	}

	@Override
	public void run() {
		VehicleEntity entity = session.getEntity();
		Player        driver = session.getDriver();
		Car           car    = session.getCar();

		// Entity was killed externally (explosion, etc.) — destroy completely, no item return
		if (!entity.isAlive()) {
			carService.destroyCar(entity.getEntityUUID(), false);
			cancel();
			return;
		}

		// Driver dismounted or disconnected — EntityDismountEvent fires and calls parkCar;
		// guard here ensures the task stops cleanly if the event fires before this tick
		if (!driver.isOnline() || driver.getVehicle() != entity.getBukkitEntity()) {
			carService.parkCar(entity.getEntityUUID());
			cancel();
			return;
		}

		// Read WASD input written by VehicleInputInterceptor from the Netty IO thread
		boolean forward  = session.isInputForward();
		boolean backward = session.isInputBackward();
		boolean left     = session.isInputLeft();
		boolean right    = session.isInputRight();

		// Fuel check: no fuel — coast to a stop
		boolean hasFuel = !car.isFuelEnabled() || session.hasFuel();
		if (!hasFuel) {
			currentSpeed = decelerate(currentSpeed, car.getDeceleration() * physicsConfig.getCarHardBrakeMultiplier());
			entity.updateMovement(currentSpeed, currentYaw);
			session.updateDisplays(buildFuelDisplay(car));
			return;
		}

		// Turning — only when moving so the car doesn't spin on the spot
		if (Math.abs(currentSpeed) > 0.001) {
			if (left) currentYaw -= (float) car.getTurnSpeed();
			if (right) currentYaw += (float) car.getTurnSpeed();
		}

		// Speed control
		if (driver.isSneaking()) {
			// Hard brake
			currentSpeed = decelerate(currentSpeed, car.getDeceleration() * physicsConfig.getCarHardBrakeMultiplier());
		} else if (forward) {
			currentSpeed = Math.min(car.getMaxSpeed(), currentSpeed + car.getAcceleration());
		} else if (backward) {
			currentSpeed = Math.max(-car.getMaxSpeed() * physicsConfig.getCarReverseSpeedRatio(),
			                        currentSpeed - car.getAcceleration());
		} else {
			// No input — friction brings speed back to zero
			currentSpeed = decelerate(currentSpeed, car.getDeceleration());
		}

		entity.updateMovement(currentSpeed, currentYaw);

		// Consume fuel when moving
		if (car.isFuelEnabled() && Math.abs(currentSpeed) > 0.001) {
			session.consumeFuel(physicsConfig.getCarFuelConsumePerTick());
		}

		session.updateDisplays(buildFuelDisplay(car));
	}

	public double getCurrentSpeed() {
		return currentSpeed;
	}

	/**
	 * Builds the action bar fuel display string, or {@code null} if fuel is not enabled.
	 */
	private String buildFuelDisplay(Car car) {
		if (!car.isFuelEnabled()) return null;
		return FuelBar.render(session.getCurrentFuel(), session.getMaxFuel());
	}

	/**
	 * Moves {@code speed} toward zero by {@code amount} without overshooting.
	 */
	private double decelerate(double speed, double amount) {
		if (speed > 0) return Math.max(0, speed - amount);
		if (speed < 0) return Math.min(0, speed + amount);
		return 0;
	}
}
