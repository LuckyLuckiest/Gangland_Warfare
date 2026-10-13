package org.luckyraven.gangland.gadget.car.vehicle;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.events.vehicle.CarMoveEvent;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.ExhaustSide;
import org.luckyraven.gangland.gadget.car.vehicle.entity.VehicleEntity;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.util.ParticleUtil;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P-VEH-1 / P-VEH-2 in the movement task: the applied speed is currentSpeed x session multiplier (the stored speed,
 * and so the HUD, keeps the driver intent), and a {@link CarMoveEvent} fires every {@code Car_Events.Move_Interval_Ticks}
 * moving ticks with the speed in blocks per second.
 */
@DisplayName("VehicleMovementTask - speed multiplier and CarMoveEvent")
class VehicleMovementTaskTest {

	private BukkitStatics               bukkit;
	private MockedStatic<ParticleUtil>  particles;
	private final List<Event>           fired = new ArrayList<>();

	private World               world;
	private VehicleSession      session;
	private VehicleEntity       entity;
	private GadgetPhysicsConfig config;
	private Entity              bukkitEntity;
	private Player              driver;
	private double              entityX;
	private boolean             entityLocationNull;

	@BeforeEach
	void setUp() {
		bukkit    = BukkitStatics.install();
		particles = mockStatic(ParticleUtil.class);
		doAnswer(invocation -> {
			fired.add(invocation.getArgument(0));
			return null;
		}).when(bukkit.pluginManager()).callEvent(any(Event.class));

		world        = mock(World.class);
		bukkitEntity = mock(Entity.class);
		driver       = mock(Player.class);
		when(driver.isOnline()).thenReturn(true);
		when(driver.getUniqueId()).thenReturn(UUID.randomUUID());
		when(driver.getLocation()).thenReturn(new Location(world, 0, 64, 0, 90f, 0f));
		when(driver.getVehicle()).thenReturn(bukkitEntity);

		entity = mock(VehicleEntity.class);
		when(entity.isAlive()).thenReturn(true);
		when(entity.getBukkitEntity()).thenReturn(bukkitEntity);
		when(entity.getLocation()).thenAnswer(invocation -> {
			if (entityLocationNull) return null;
			entityX += 0.5;
			return new Location(world, entityX, 64, 0);
		});

		Car car = Car.builder()
		             .carId("test_car")
		             .displayName("Test Car")
		             .itemMaterial(Material.MINECART)
		             .lore(List.of())
		             .maxSpeed(0.8)
		             .acceleration(0.5)
		             .deceleration(0.02)
		             .turnSpeed(4.0)
		             .maxHealth(100.0)
		             .fuelEnabled(false)
		             .maxDurability(500)
		             .build();

		session = mock(VehicleSession.class);
		when(session.getEntity()).thenReturn(entity);
		when(session.getDriver()).thenReturn(driver);
		when(session.getCar()).thenReturn(car);
		when(session.getExhaustSide()).thenReturn(ExhaustSide.LEFT);
		when(session.getSpeedMultiplier()).thenReturn(1.0);
		when(session.isInputForward()).thenReturn(true);

		config = mock(GadgetPhysicsConfig.class);
		when(config.getCarMoveIntervalTicks()).thenReturn(4);
		when(config.getCarHardBrakeMultiplier()).thenReturn(3.0);
	}

	@AfterEach
	void tearDown() {
		particles.close();
		bukkit.close();
	}

	private VehicleMovementTask task() {
		return new VehicleMovementTask(session, mock(CarService.class), config);
	}

	private List<CarMoveEvent> moveEvents() {
		return fired.stream().filter(CarMoveEvent.class::isInstance).map(CarMoveEvent.class::cast).toList();
	}

	@Test
	@DisplayName("appliedSpeedIsScaled: multiplier 0.5 -> entity.updateMovement receives half the current speed")
	void appliedSpeedIsScaled() {
		when(session.getSpeedMultiplier()).thenReturn(0.5);
		VehicleMovementTask task = task();

		task.run();

		verify(entity, atLeastOnce()).updateMovement(doubleThat(speed -> Math.abs(speed - 0.25) < 1e-9), anyFloat());
		assertEquals(0.5, task.getCurrentSpeed(), 1e-9, "stored speed keeps the driver intent for the HUD");
	}

	@Test
	@DisplayName("multiplierZeroHoldsCarStill: 0.0 applies speed 0 while the stored speed still builds")
	void multiplierZeroHoldsCarStill() {
		when(session.getSpeedMultiplier()).thenReturn(0.0);
		VehicleMovementTask task = task();

		task.run();

		verify(entity, atLeastOnce()).updateMovement(doubleThat(speed -> Math.abs(speed) < 1e-9), anyFloat());
		assertEquals(0.5, task.getCurrentSpeed(), 1e-9);
	}

	@Test
	@DisplayName("multiplierChangeMidDriveAppliesNextTick: the multiplier is read every tick, not cached")
	void multiplierChangeMidDriveAppliesNextTick() {
		VehicleMovementTask task = task();
		task.run();
		when(session.getSpeedMultiplier()).thenReturn(0.5);

		task.run(); // stored speed 0.8 (max) -> applied 0.4

		verify(entity, atLeastOnce()).updateMovement(doubleThat(speed -> Math.abs(speed - 0.4) < 1e-9), anyFloat());
	}

	@Test
	@DisplayName("moveEventEveryIntervalWhileMoving: interval 4, 12 moving ticks -> 3 CarMoveEvents")
	void moveEventEveryIntervalWhileMoving() {
		VehicleMovementTask task = task();

		for (int tick = 0; tick < 12; tick++) {
			task.run();
		}

		assertEquals(3, moveEvents().size());
	}

	@Test
	@DisplayName("stationaryTicksFireNothing: no input, no speed -> no CarMoveEvent")
	void stationaryTicksFireNothing() {
		when(session.isInputForward()).thenReturn(false);
		VehicleMovementTask task = task();

		for (int tick = 0; tick < 12; tick++) {
			task.run();
		}

		assertEquals(0, moveEvents().size());
	}

	@Test
	@DisplayName("moveEventSpeedInBlocksPerSecond: 0.5 blocks/tick -> event speed 10.0, driver and vehicle set")
	void moveEventSpeedInBlocksPerSecond() {
		when(config.getCarMoveIntervalTicks()).thenReturn(1);
		VehicleMovementTask task = task();

		task.run();

		List<CarMoveEvent> events = moveEvents();
		assertEquals(1, events.size());
		assertEquals(10.0, events.get(0).getBlocksPerSecond(), 1e-9);
		assertSame(driver, events.get(0).getDriver());
		assertSame(bukkitEntity, events.get(0).getVehicle());
	}

	@Test
	@DisplayName("moveEventReportsAppliedSpeed: multiplier 0.5 -> 5.0 blocks/s, what the car really does")
	void moveEventReportsAppliedSpeed() {
		when(config.getCarMoveIntervalTicks()).thenReturn(1);
		when(session.getSpeedMultiplier()).thenReturn(0.5);
		VehicleMovementTask task = task();

		task.run();

		assertEquals(1, moveEvents().size());
		assertEquals(5.0, moveEvents().get(0).getBlocksPerSecond(), 1e-9);
	}

	@Test
	@DisplayName("moveEventCarriesFromAndTo: from is where the car was at the previous event, to where it is now")
	void moveEventCarriesFromAndTo() {
		VehicleMovementTask task = task();

		for (int tick = 0; tick < 8; tick++) {
			task.run();
		}

		List<CarMoveEvent> events = moveEvents();
		assertEquals(2, events.size());
		assertEquals(events.get(0).getTo().getX(), events.get(1).getFrom().getX(), 1e-9);
	}

	@Test
	@DisplayName("intervalBelowOneIsClampedToOne: a 0 or negative knob must not divide by zero or silence the event")
	void intervalBelowOneIsClampedToOne() {
		when(config.getCarMoveIntervalTicks()).thenReturn(0);
		VehicleMovementTask task = task();

		for (int tick = 0; tick < 3; tick++) {
			assertDoesNotThrow(task::run);
		}

		assertEquals(3, moveEvents().size());
	}

	@Test
	@DisplayName("nullEntityLocationFiresNothing: GD-26, MinecartVehicle.getLocation may be null")
	void nullEntityLocationFiresNothing() {
		entityLocationNull = true;
		when(config.getCarMoveIntervalTicks()).thenReturn(1);
		VehicleMovementTask task = task();

		for (int tick = 0; tick < 3; tick++) {
			assertDoesNotThrow(task::run);
		}

		assertEquals(0, moveEvents().size());
	}
}
