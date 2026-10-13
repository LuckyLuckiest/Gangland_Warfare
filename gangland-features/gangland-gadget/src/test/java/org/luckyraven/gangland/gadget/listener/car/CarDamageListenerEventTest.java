package org.luckyraven.gangland.gadget.listener.car;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.events.vehicle.CarDamageEvent;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.access.CarAccessPolicy;
import org.luckyraven.gangland.gadget.car.message.CarMessageContract;
import org.luckyraven.gangland.gadget.car.vehicle.ParkedVehicle;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleRegistry;
import org.luckyraven.gangland.gadget.car.vehicle.entity.VehicleEntity;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P-VEH-1: {@link CarDamageListener} publishes the cancellable {@link CarDamageEvent} before applying damage to a
 * gadget car and honours a cancel. Also: an owner pickup drops any hotwire grant (the pickup path itself is not
 * widened, GD-06 stays).
 */
@DisplayName("CarDamageListener - CarDamageEvent")
class CarDamageListenerEventTest {

	private BukkitStatics     bukkit;
	private final List<Event> fired = new ArrayList<>();
	private boolean           cancelCarDamage;

	private CarService      carService;
	private CarAccessPolicy policy;
	private CarDamageListener listener;
	private UUID            carUuid;
	private ParkedVehicle   parked;
	private UUID            placer;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		doAnswer(invocation -> {
			Object event = invocation.getArgument(0);
			fired.add((Event) event);
			if (cancelCarDamage && event instanceof CarDamageEvent damage) damage.setCancelled(true);
			return null;
		}).when(bukkit.pluginManager()).callEvent(any(Event.class));

		carUuid = UUID.randomUUID();
		placer  = UUID.randomUUID();

		VehicleEntity vehicleEntity = mock(VehicleEntity.class);
		parked = mock(ParkedVehicle.class);
		when(parked.getEntity()).thenReturn(vehicleEntity);
		when(parked.getDurability()).thenReturn(500);
		when(parked.getPlacerUUID()).thenReturn(placer);

		carService = mock(CarService.class);
		when(carService.getVehicleRegistry()).thenReturn(new VehicleRegistry());
		when(carService.getParkedVehicle(carUuid)).thenReturn(parked);
		when(carService.getPlugin()).thenReturn(mock(JavaPlugin.class));

		policy   = new CarAccessPolicy((one, other) -> false);
		listener = new CarDamageListener(carService, policy, mock(CarMessageContract.class), new CarDamageState());
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private Entity carEntity() {
		Entity entity = mock(Entity.class);
		when(entity.getUniqueId()).thenReturn(carUuid);
		return entity;
	}

	private EntityDamageEvent explosion(Entity entity, double damage) {
		EntityDamageEvent event = mock(EntityDamageEvent.class);
		when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.BLOCK_EXPLOSION);
		when(event.getEntity()).thenReturn(entity);
		when(event.getDamage()).thenReturn(damage);
		return event;
	}

	private VehicleDamageEvent punch(Player attacker, double damage) {
		Minecart cart = mock(Minecart.class);
		when(cart.getUniqueId()).thenReturn(carUuid);
		VehicleDamageEvent event = mock(VehicleDamageEvent.class);
		when(event.getVehicle()).thenReturn(cart);
		when(event.getAttacker()).thenReturn(attacker);
		when(event.getDamage()).thenReturn(damage);
		return event;
	}

	private List<CarDamageEvent> carDamageEvents() {
		return fired.stream().filter(CarDamageEvent.class::isInstance).map(CarDamageEvent.class::cast).toList();
	}

	@Test
	@DisplayName("environmentalDamageFiresCarDamageEventWithNullDamager and applies the damage when nobody cancels")
	void environmentalDamageFiresCarDamageEvent() {
		Entity entity = carEntity();

		listener.onEntityDamage(explosion(entity, 7.0));

		assertEquals(1, carDamageEvents().size());
		CarDamageEvent event = carDamageEvents().get(0);
		assertSame(entity, event.getVehicle());
		assertNull(event.getDamager());
		assertEquals(7.0, event.getDamage(), 1e-9);
		verify(carService).damageParkedCar(carUuid, 7);
	}

	@Test
	@DisplayName("cancelledCarDamageEventSkipsDamage: a cancelling listener leaves the durability untouched")
	void cancelledCarDamageEventSkipsDamage() {
		cancelCarDamage = true;

		listener.onEntityDamage(explosion(carEntity(), 7.0));

		assertEquals(1, carDamageEvents().size());
		verify(carService, never()).damageParkedCar(any(UUID.class), anyInt());
	}

	@Test
	@DisplayName("playerPunchFiresCarDamageEventWithThePlayerAsDamager")
	void playerPunchFiresCarDamageEvent() {
		Player attacker = mock(Player.class);
		when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());

		listener.onVehicleDamage(punch(attacker, 2.0));

		assertEquals(1, carDamageEvents().size());
		assertSame(attacker, carDamageEvents().get(0).getDamager());
		assertEquals(2.0, carDamageEvents().get(0).getDamage(), 1e-9);
		verify(carService).damageParkedCar(carUuid, 2);
	}

	@Test
	@DisplayName("cancelledPunchSkipsDamage")
	void cancelledPunchSkipsDamage() {
		cancelCarDamage = true;
		Player attacker = mock(Player.class);
		when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());

		listener.onVehicleDamage(punch(attacker, 2.0));

		verify(carService, never()).damageParkedCar(any(UUID.class), anyInt());
	}

	@Test
	@DisplayName("notACarFiresNothing: damage to an unregistered entity publishes no CarDamageEvent")
	void notACarFiresNothing() {
		Entity other = mock(Entity.class);
		when(other.getUniqueId()).thenReturn(UUID.randomUUID());

		listener.onEntityDamage(explosion(other, 3.0));

		assertTrue(carDamageEvents().isEmpty());
	}

	@Test
	@DisplayName("ownerPickupDropsHotwireGrant: the thief loses the car once the owner picks it up")
	void ownerPickupDropsHotwireGrant() {
		UUID thief = UUID.randomUUID();
		policy.grant(carUuid, thief);

		Player owner = mock(Player.class);
		when(owner.getUniqueId()).thenReturn(placer);
		when(owner.isSneaking()).thenReturn(true);

		listener.onVehicleDamage(punch(owner, 1.0));

		verify(carService).pickupCar(eq(owner), eq(carUuid));
		assertNull(policy.grantedTo(carUuid));
	}

	@Test
	@DisplayName("strangerPickupStillDeniedAndKeepsGrant: GD-06 is not widened")
	void strangerPickupStillDeniedAndKeepsGrant() {
		UUID thief = UUID.randomUUID();
		policy.grant(carUuid, thief);

		Player stranger = mock(Player.class);
		when(stranger.getUniqueId()).thenReturn(UUID.randomUUID());
		when(stranger.isSneaking()).thenReturn(true);

		listener.onVehicleDamage(punch(stranger, 1.0));

		verify(carService, never()).pickupCar(any(Player.class), any(UUID.class));
		assertEquals(thief, policy.grantedTo(carUuid));
	}
}
