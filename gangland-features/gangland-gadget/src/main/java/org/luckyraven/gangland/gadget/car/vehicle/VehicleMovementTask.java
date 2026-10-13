package org.luckyraven.gangland.gadget.car.vehicle;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.luckyraven.gangland.events.vehicle.CarMoveEvent;
import org.luckyraven.keystone.util.ParticleUtil;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.ExhaustSide;
import org.luckyraven.gangland.gadget.car.vehicle.entity.VehicleEntity;
import org.luckyraven.gangland.gadget.car.vehicle.packet.VehicleInputInterceptor;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.item.fuel.FuelBar;

/**
 * Tick-based movement handler for an active car. Runs every server tick (50 ms) while a player is driving.
 *
 * <p>WASD keys control movement. Input is written into {@link VehicleSession} by
 * {@link VehicleInputInterceptor} (Netty IO thread) each time the client sends a steering packet, and read here on the
 * main thread. W speeds up forward, S brakes then reverses, A/D steer, W+S triggers a burnout spin. Fuel is consumed
 * each tick if fuel is enabled.
 */
public class VehicleMovementTask extends BukkitRunnable {

	private final VehicleSession      session;
	private final CarService          carService;
	private final GadgetPhysicsConfig physicsConfig;

	@Getter
	private double currentSpeed;
	private float  currentYaw;
	private int      movingTicks;
	private Location lastEventLocation;

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

		if (checkGuards(entity, driver)) return;

		boolean forward  = session.isInputForward();
		boolean backward = session.isInputBackward();
		boolean left     = session.isInputLeft();
		boolean right    = session.isInputRight();

		if (!car.isFuelEnabled() || session.hasFuel()) {
			if (forward && backward) {
				tickBurnout(entity, car, left, right);
				return;
			}
			updateSteering(driver, forward, left, right);
			updateSpeed(car, forward, backward);
		} else {
			coastToStop(car);
		}

		finalizeTick(entity, car);
	}

	/**
	 * Checks whether the entity or driver are in an invalid state. Cancels the task and triggers the appropriate
	 * cleanup if so.
	 *
	 * @return {@code true} if the tick should be aborted
	 */
	private boolean checkGuards(VehicleEntity entity, Player driver) {
		if (!entity.isAlive()) {
			Location fallback = driver.isOnline() ? driver.getLocation() : null;
			carService.forcePark(entity.getEntityUUID(), fallback);
			cancel();
			return true;
		}

		if (!driver.isOnline() || driver.getVehicle() != entity.getBukkitEntity()) {
			carService.parkCar(entity.getEntityUUID());
			cancel();
			return true;
		}

		return false;
	}

	/**
	 * Handles the burnout state — W+S held simultaneously. Brakes the car to a stop, lets A/D spin the heading, and
	 * emits tire-smoke particles.
	 */
	private void tickBurnout(VehicleEntity entity, Car car, boolean left, boolean right) {
		currentSpeed = decelerate(currentSpeed, car.getDeceleration() * physicsConfig.getCarHardBrakeMultiplier());

		if (left) currentYaw -= (float) car.getTurnSpeed();
		if (right) currentYaw += (float) car.getTurnSpeed();

		entity.updateMovement(appliedSpeed(currentSpeed), currentYaw);
		entity.setFacing(currentYaw);
		ParticleUtil.spawnBurnoutSmoke(entity.getLocation(), currentYaw);

		World world = entity.getLocation().getWorld();
		if (world != null) {
			world.playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 1.5f);
		}

		if (car.isFuelEnabled()) session.consumeFuel(physicsConfig.getCarFuelConsumePerTick());

		Location live = entity.getLocation();
		if (live != null && live.getWorld() != null) {
			session.setLastKnownLocation(live.clone());
		}

		session.updateDisplays(buildFuelDisplay(car));
	}

	/**
	 * Decelerates to a stop when the car has no fuel. Does not modify steering.
	 */
	private void coastToStop(Car car) {
		currentSpeed = decelerate(currentSpeed, car.getDeceleration() * physicsConfig.getCarHardBrakeMultiplier());
	}

	/**
	 * Updates {@link #currentYaw} based on driver input.
	 * <p>
	 * W pressed from a stop re-aligns the heading to the driver's look direction. A/D steers while moving. When neither
	 * condition applies (coasting, decelerating with no A/D input) the heading is preserved.
	 */
	private void updateSteering(Player driver, boolean forward, boolean left, boolean right) {
		if (forward && Math.abs(currentSpeed) <= 0.001) {
			currentYaw = driver.getLocation().getYaw();
		} else if (Math.abs(currentSpeed) > 0.001 && (left || right)) {
			if (left) currentYaw -= (float) session.getCar().getTurnSpeed();
			if (right) currentYaw += (float) session.getCar().getTurnSpeed();
		}
	}

	/**
	 * Updates {@link #currentSpeed} based on driver input. S brakes before reversing.
	 */
	private void updateSpeed(Car car, boolean forward, boolean backward) {
		if (forward) {
			currentSpeed = Math.min(car.getMaxSpeed(), currentSpeed + car.getAcceleration());
		} else if (backward) {
			if (currentSpeed > 0) {
				currentSpeed = decelerate(currentSpeed,
				                          car.getDeceleration() * physicsConfig.getCarHardBrakeMultiplier());
			} else {
				currentSpeed = Math.max(-car.getMaxSpeed() * physicsConfig.getCarReverseSpeedRatio(),
				                        currentSpeed - car.getAcceleration());
			}
		} else {
			currentSpeed = decelerate(currentSpeed, car.getDeceleration());
		}
	}

	/**
	 * Applies the current speed and yaw to the entity, spawns exhaust particles, consumes fuel, and refreshes HUD
	 * displays.
	 */
	private void finalizeTick(VehicleEntity entity, Car car) {
		double applied = appliedSpeed(currentSpeed);
		entity.updateMovement(applied, currentYaw);
		if (Math.abs(currentSpeed) > 0.001) {
			movingTicks++;
			int interval = Math.max(1, physicsConfig.getCarMoveIntervalTicks());
			if (movingTicks % interval == 0) fireMove(entity, applied);

			ExhaustSide side = session.getExhaustSide();

			ParticleUtil.spawnCarExhaust(entity.getLocation(), currentYaw,
			                             side == ExhaustSide.LEFT || side == ExhaustSide.BOTH,
			                             side == ExhaustSide.RIGHT || side == ExhaustSide.BOTH);

			if (car.isFuelEnabled()) session.consumeFuel(physicsConfig.getCarFuelConsumePerTick());
		}

		Location live = entity.getLocation();
		if (live != null && live.getWorld() != null) {
			session.setLastKnownLocation(live.clone());
		}

		session.updateDisplays(buildFuelDisplay(car));
	}

	/**
	 * Speed actually applied to the entity: the driver's stored speed scaled by the session multiplier. The stored speed
	 * itself stays unscaled so the HUD keeps the driver intent.
	 */
	private double appliedSpeed(double speed) {
		return speed * session.getSpeedMultiplier();
	}

	/**
	 * Fires a {@link CarMoveEvent} with the applied speed in blocks per second (blocks per tick x 20). The "from" point is
	 * where the car was at the previous event, or the current point for the first one.
	 */
	private void fireMove(VehicleEntity entity, double applied) {
		Location to = entity.getLocation();
		if (to == null || to.getWorld() == null) return;

		Location from = lastEventLocation != null ? lastEventLocation : to;
		Bukkit.getPluginManager().callEvent(new CarMoveEvent(session.getDriver(), entity.getBukkitEntity(), from, to,
		                                                     Math.abs(applied) * 20.0));
		lastEventLocation = to.clone();
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
