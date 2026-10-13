package org.luckyraven.gangland.gadget.listener.car;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.access.CarAccessPolicy;
import org.luckyraven.gangland.gadget.car.message.CarMessageContract;
import org.luckyraven.gangland.gadget.car.theft.HotwireService;
import org.luckyraven.gangland.gadget.car.vehicle.ParkedVehicle;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleRegistry;
import org.luckyraven.gangland.item.fuel.Fuel;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P-CRIME-1 at the click: a stranger holding the lockpick starts a hotwire, everyone else behaves exactly as GD-06
 * left it (owner, gang mate, unowned car mount; a stranger without a lockpick is refused). A granted thief may mount
 * but not refuel (refuel keeps canUse).
 */
@DisplayName("CarEntityInteractListener - hotwire entry")
class CarEntityInteractListenerHotwireTest {

	private static final String NO_PERMISSION = "no-permission";

	private CarService         carService;
	private CarMessageContract messages;
	private HotwireService     hotwire;
	private CarAccessPolicy    policy;
	private boolean            sharesGang;
	private CarEntityInteractListener listener;

	private MockedStatic<Fuel> fuel;
	private UUID               carUuid;
	private UUID               placer;
	private ParkedVehicle      parked;
	private Entity             clicked;
	private ItemStack          held;

	@BeforeEach
	void setUp() {
		fuel = mockStatic(Fuel.class);

		carUuid = UUID.randomUUID();
		placer  = UUID.randomUUID();

		Car car = mock(Car.class);
		when(car.getCarId()).thenReturn("sports_car");
		parked = mock(ParkedVehicle.class);
		when(parked.getPlacerUUID()).thenReturn(placer);
		when(parked.getCar()).thenReturn(car);

		carService = mock(CarService.class);
		when(carService.isParkedVehicle(carUuid)).thenReturn(true);
		when(carService.getParkedVehicle(carUuid)).thenReturn(parked);
		VehicleRegistry registry = mock(VehicleRegistry.class);
		when(registry.isPlayerDriving(any(UUID.class))).thenReturn(false);
		when(carService.getVehicleRegistry()).thenReturn(registry);

		messages = mock(CarMessageContract.class);
		when(messages.noPermission()).thenReturn(NO_PERMISSION);

		hotwire = mock(HotwireService.class);
		when(hotwire.isEnabled()).thenReturn(true);
		when(hotwire.start(any(Player.class), any(Entity.class), any(), anyString())).thenReturn(true);

		policy   = new CarAccessPolicy((one, other) -> sharesGang);
		listener = new CarEntityInteractListener(carService, messages, policy, hotwire);

		clicked = mock(Entity.class);
		when(clicked.getUniqueId()).thenReturn(carUuid);
		held = mock(ItemStack.class);
	}

	@AfterEach
	void tearDown() {
		fuel.close();
	}

	private Player player(UUID uuid, boolean lockpickInHand) {
		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getItemInMainHand()).thenReturn(held);
		when(hotwire.isLockpick(held)).thenReturn(lockpickInHand);
		return player;
	}

	private PlayerInteractEntityEvent click(Player player) {
		PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
		when(event.getHand()).thenReturn(EquipmentSlot.HAND);
		when(event.getPlayer()).thenReturn(player);
		when(event.getRightClicked()).thenReturn(clicked);
		return event;
	}

	@Test
	@DisplayName("strangerWithLockpickStartsHotwire: start(thief, car, owner, type); no mount, no refusal")
	void strangerWithLockpickStartsHotwire() {
		Player thief = player(UUID.randomUUID(), true);

		listener.onInteractEntity(click(thief));

		verify(hotwire).start(thief, clicked, placer, "sports_car");
		verify(carService, never()).mountCar(any(Player.class), any(UUID.class));
		verify(thief, never()).sendMessage(NO_PERMISSION);
	}

	@Test
	@DisplayName("strangerWithoutLockpickIsRefused: GD-06 message, nothing starts")
	void strangerWithoutLockpickIsRefused() {
		Player stranger = player(UUID.randomUUID(), false);

		listener.onInteractEntity(click(stranger));

		verify(stranger).sendMessage(NO_PERMISSION);
		verify(hotwire, never()).start(any(Player.class), any(Entity.class), any(), anyString());
		verify(carService, never()).mountCar(any(Player.class), any(UUID.class));
	}

	@Test
	@DisplayName("hotwireDisabledRefuses: Hotwire.Enable false -> lockpick is just an item")
	void hotwireDisabledRefuses() {
		when(hotwire.isEnabled()).thenReturn(false);
		Player thief = player(UUID.randomUUID(), true);

		listener.onInteractEntity(click(thief));

		verify(thief).sendMessage(NO_PERMISSION);
		verify(hotwire, never()).start(any(Player.class), any(Entity.class), any(), anyString());
	}

	@Test
	@DisplayName("ownerWithLockpickJustMounts: no hotwire for a car you may use (guard, green at skeleton)")
	void ownerWithLockpickJustMounts() {
		Player owner = player(placer, true);

		listener.onInteractEntity(click(owner));

		verify(carService).mountCar(owner, carUuid);
		verify(hotwire, never()).start(any(Player.class), any(Entity.class), any(), anyString());
	}

	@Test
	@DisplayName("gangMateWithLockpickJustMounts (guard, green at skeleton)")
	void gangMateWithLockpickJustMounts() {
		sharesGang = true;
		Player mate = player(UUID.randomUUID(), true);

		listener.onInteractEntity(click(mate));

		verify(carService).mountCar(mate, carUuid);
		verify(hotwire, never()).start(any(Player.class), any(Entity.class), any(), anyString());
	}

	@Test
	@DisplayName("unownedCarStaysOpen: a car with no recorded placer mounts without any lockpick (guard)")
	void unownedCarStaysOpen() {
		when(parked.getPlacerUUID()).thenReturn(null);
		Player anyone = player(UUID.randomUUID(), true);

		listener.onInteractEntity(click(anyone));

		verify(carService).mountCar(anyone, carUuid);
		verify(hotwire, never()).start(any(Player.class), any(Entity.class), any(), anyString());
	}

	@Test
	@DisplayName("grantedThiefMountsWithoutRefusal: after a finished hotwire the right-click drives the car")
	void grantedThiefMounts() {
		UUID   thiefId = UUID.randomUUID();
		Player thief   = player(thiefId, false);
		policy.grant(carUuid, thiefId);

		listener.onInteractEntity(click(thief));

		verify(carService).mountCar(thief, carUuid);
		verify(thief, never()).sendMessage(NO_PERMISSION);
	}

	@Test
	@DisplayName("grantedThiefCannotRefuel: refuel keeps canUse, so a fuel can is refused")
	void grantedThiefCannotRefuel() {
		UUID   thiefId = UUID.randomUUID();
		Player thief   = player(thiefId, false);
		policy.grant(carUuid, thiefId);
		fuel.when(() -> Fuel.isFuelItem(held)).thenReturn(true);

		listener.onInteractEntity(click(thief));

		verify(thief).sendMessage(NO_PERMISSION);
		verify(carService, never()).refuelParkedCar(any(UUID.class), anyInt());
		verify(carService, never()).mountCar(any(Player.class), any(UUID.class));
	}

	@Test
	@DisplayName("sneakClickIsIgnored: shift + right-click never starts a hotwire (D-C1: plain right-click)")
	void sneakClickIsIgnored() {
		Player thief = player(UUID.randomUUID(), true);
		when(thief.isSneaking()).thenReturn(true);

		listener.onInteractEntity(click(thief));

		verify(hotwire, never()).start(any(Player.class), any(Entity.class), any(), anyString());
	}

	@Test
	@DisplayName("cancelledStartShowsNoRefusal: start() false (event cancelled / already hotwiring) is silent here")
	void cancelledStartIsHandledByService() {
		when(hotwire.start(any(Player.class), any(Entity.class), any(), anyString())).thenReturn(false);
		Player thief = player(UUID.randomUUID(), true);

		listener.onInteractEntity(click(thief));

		verify(hotwire).start(thief, clicked, placer, "sports_car");
		verify(carService, never()).mountCar(any(Player.class), any(UUID.class));
		verify(thief, never()).sendMessage(anyString());
	}
}
