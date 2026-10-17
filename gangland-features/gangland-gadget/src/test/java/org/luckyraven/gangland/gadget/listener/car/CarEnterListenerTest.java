package org.luckyraven.gangland.gadget.listener.car;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.events.vehicle.CarEnterEvent;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.vehicle.ParkedVehicle;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleRegistry;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleSession;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P-VEH-1: entering a gadget car publishes {@link CarEnterEvent}; any other vehicle is ignored. The handler is a pure
 * observer (MONITOR, ignoreCancelled) so it can never double-cancel the cops or gang listeners.
 */
@DisplayName("CarEnterListener - VehicleEnterEvent -> CarEnterEvent")
class CarEnterListenerTest {

	private BukkitStatics     bukkit;
	private final List<Event> fired = new ArrayList<>();
	private CarService        carService;
	private VehicleRegistry   registry;
	private CarEnterListener  listener;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		doAnswer(invocation -> {
			fired.add(invocation.getArgument(0));
			return null;
		}).when(bukkit.pluginManager()).callEvent(any(Event.class));

		carService = mock(CarService.class);
		registry   = mock(VehicleRegistry.class);
		when(carService.getVehicleRegistry()).thenReturn(registry);
		listener = new CarEnterListener(carService);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private static Car car(String id) {
		Car car = mock(Car.class);
		when(car.getCarId()).thenReturn(id);
		return car;
	}

	private static Minecart minecart(UUID uuid) {
		Minecart minecart = mock(Minecart.class);
		when(minecart.getUniqueId()).thenReturn(uuid);
		return minecart;
	}

	private static VehicleEnterEvent enter(Entity entered, Minecart vehicle) {
		VehicleEnterEvent event = mock(VehicleEnterEvent.class);
		when(event.getEntered()).thenReturn(entered);
		when(event.getVehicle()).thenReturn(vehicle);
		return event;
	}

	@Test
	@DisplayName("firesOnlyForGadgetCars: plain minecart -> no event")
	void plainMinecartFiresNothing() {
		Player player = mock(Player.class);

		Minecart cart = minecart(UUID.randomUUID());
		listener.onEnter(enter(player, cart));

		assertTrue(fired.isEmpty());
	}

	@Test
	@DisplayName("firesOnlyForGadgetCars: a driven (session) car -> CarEnterEvent with the car type")
	void sessionCarFiresCarEnterEvent() {
		UUID          uuid    = UUID.randomUUID();
		Player        player  = mock(Player.class);
		Minecart      cart    = minecart(uuid);
		VehicleSession session = mock(VehicleSession.class);
		Car sessionCar = car("sports_car");
		when(session.getCar()).thenReturn(sessionCar);
		when(registry.getByEntity(uuid)).thenReturn(session);

		listener.onEnter(enter(player, cart));

		assertEquals(1, fired.size());
		CarEnterEvent event = (CarEnterEvent) fired.get(0);
		assertSame(player, event.getPlayer());
		assertSame(cart, event.getVehicle());
		assertEquals("sports_car", event.getCarType());
	}

	@Test
	@DisplayName("parkedCarAlsoFires: a parked gadget car resolves its type from the parked record")
	void parkedCarFiresCarEnterEvent() {
		UUID          uuid   = UUID.randomUUID();
		Player        player = mock(Player.class);
		ParkedVehicle parked = mock(ParkedVehicle.class);
		Car parkedCar = car("van");
		when(parked.getCar()).thenReturn(parkedCar);
		when(carService.getParkedVehicle(uuid)).thenReturn(parked);

		Minecart cart = minecart(uuid);
		listener.onEnter(enter(player, cart));

		assertEquals(1, fired.size());
		assertEquals("van", ((CarEnterEvent) fired.get(0)).getCarType());
	}

	@Test
	@DisplayName("nonPlayerEnteringIsIgnored: a mob boarding a gadget car is not a CarEnterEvent")
	void nonPlayerEnteringIsIgnored() {
		UUID           uuid    = UUID.randomUUID();
		VehicleSession session = mock(VehicleSession.class);
		Car sessionCar = car("sports_car");
		when(session.getCar()).thenReturn(sessionCar);
		when(registry.getByEntity(uuid)).thenReturn(session);

		Minecart cart = minecart(uuid);
		listener.onEnter(enter(mock(Entity.class), cart));

		assertTrue(fired.isEmpty());
	}

	@Test
	@DisplayName("neverCancelsTheBukkitEvent: read-only observer")
	void neverCancelsTheBukkitEvent() {
		UUID           uuid    = UUID.randomUUID();
		VehicleSession session = mock(VehicleSession.class);
		Car sessionCar = car("sports_car");
		when(session.getCar()).thenReturn(sessionCar);
		when(registry.getByEntity(uuid)).thenReturn(session);
		Minecart cart  = minecart(uuid);
		VehicleEnterEvent event = enter(mock(Player.class), cart);

		listener.onEnter(event);

		verify(event, never()).setCancelled(anyBoolean());
		assertEquals(1, fired.size());
	}

	@Test
	@DisplayName("handlerIsMonitorAndIgnoreCancelled: cancelled boardings (cops, gang containment) never count")
	void handlerIsMonitorAndIgnoreCancelled() throws Exception {
		EventHandler handler = CarEnterListener.class.getMethod("onEnter", VehicleEnterEvent.class)
		                                             .getAnnotation(EventHandler.class);

		assertNotNull(handler, "onEnter must be an @EventHandler");

		assertEquals(EventPriority.MONITOR, handler.priority());
		assertTrue(handler.ignoreCancelled());
	}
}
