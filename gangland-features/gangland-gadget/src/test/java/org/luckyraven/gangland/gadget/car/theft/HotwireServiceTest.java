package org.luckyraven.gangland.gadget.car.theft;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.gangland.events.vehicle.CarTheftAttemptEvent;
import org.luckyraven.gangland.gadget.car.access.CarAccessPolicy;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.util.ActionBarManager;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P-CRIME-1: hotwire timeline. {@code tick()} is one 10-tick step, so the default 6 seconds is 12 ticks. A finished
 * hotwire grants the thief the car (in memory), marks the entity PDC, uses up one lockpick and commits exactly one
 * Car_Theft through the 3-arg CrimeService.commit; ownership never transfers (T-73).
 */
@DisplayName("HotwireService - timer, grant, Car_Theft")
class HotwireServiceTest {

	private static final UUID OWNER = UUID.randomUUID();
	private static final UUID THIEF = UUID.randomUUID();
	private static final int  STEPS = 12;

	private BukkitStatics                bukkit;
	private MockedStatic<ActionBarManager> actionBar;
	private final List<Event>            fired = new ArrayList<>();
	private boolean                      cancelTheftAttempt;

	private World               world;
	private JavaPlugin          plugin;
	private CarAccessPolicy     policy;
	private CrimeService        crimes;
	private GadgetPhysicsConfig config;
	private HotwireMessages     messages;
	private HotwireService      service;

	private Player                thief;
	private PlayerInventory       inventory;
	private ItemStack             lockpick;
	private Entity                car;
	private PersistentDataContainer carPdc;
	private UUID                  carUuid;
	private Location              carLocation;
	private double                thiefX;
	private boolean               thiefOnline;
	private boolean               carDead;
	private boolean               lockpickCarried;

	@BeforeEach
	void setUp() {
		bukkit    = BukkitStatics.install();
		actionBar = mockStatic(ActionBarManager.class);
		doAnswer(invocation -> {
			Event event = invocation.getArgument(0);
			fired.add(event);
			if (cancelTheftAttempt && event instanceof CarTheftAttemptEvent attempt) attempt.setCancelled(true);
			return null;
		}).when(bukkit.pluginManager()).callEvent(any(Event.class));

		world = mock(World.class);

		plugin = mock(JavaPlugin.class);
		when(plugin.getName()).thenReturn("gangland");

		policy = new CarAccessPolicy((one, other) -> false);
		crimes = mock(CrimeService.class);

		config = mock(GadgetPhysicsConfig.class);
		when(config.isHotwireEnabled()).thenReturn(true);
		when(config.getHotwireSeconds()).thenReturn(6);
		when(config.getHotwireMaxDistance()).thenReturn(3.0);
		when(config.isHotwireConsumeLockpick()).thenReturn(true);
		when(config.getHotwireLockpickItem()).thenReturn("lockpick");

		messages = mock(HotwireMessages.class);
		when(messages.progress(anyInt())).thenReturn("progress");
		when(messages.done()).thenReturn("done");
		when(messages.cancel()).thenReturn("cancel");
		when(messages.noLockpick()).thenReturn("no-lockpick");

		lockpick = mock(ItemStack.class);
		when(lockpick.getAmount()).thenReturn(3);
		lockpickCarried = true;

		inventory = mock(PlayerInventory.class);
		when(inventory.getItemInMainHand()).thenReturn(lockpick);
		when(inventory.getContents()).thenAnswer(invocation -> new ItemStack[]{lockpickCarried ? lockpick : null});

		thiefOnline = true;
		thiefX      = 0;
		thief       = mock(Player.class);
		when(thief.getUniqueId()).thenReturn(THIEF);
		when(thief.isOnline()).thenAnswer(invocation -> thiefOnline);
		when(thief.getInventory()).thenReturn(inventory);
		when(thief.getLocation()).thenAnswer(invocation -> new Location(world, thiefX, 64, 0));

		carUuid     = UUID.randomUUID();
		carLocation = new Location(world, 1, 64, 0);
		carPdc      = mock(PersistentDataContainer.class);
		car         = mock(Entity.class);
		when(car.getUniqueId()).thenReturn(carUuid);
		when(car.getLocation()).thenReturn(carLocation);
		when(car.isValid()).thenAnswer(invocation -> !carDead);
		when(car.isDead()).thenAnswer(invocation -> carDead);
		when(car.getPersistentDataContainer()).thenReturn(carPdc);

		service = new HotwireService(plugin, policy, crimes, config, messages,
		                             stack -> stack == lockpick ? "lockpick" : null);
	}

	@AfterEach
	void tearDown() {
		actionBar.close();
		bukkit.close();
	}

	private void ticks(int count) {
		for (int i = 0; i < count; i++) {
			service.tick();
		}
	}

	@Test
	@DisplayName("isLockpickMatchesConfiguredKey: only the Hotwire.Lockpick_Item unique key counts")
	void isLockpickMatchesConfiguredKey() {
		ItemStack other = mock(ItemStack.class);

		assertTrue(service.isLockpick(lockpick));
		assertFalse(service.isLockpick(other));
		assertFalse(service.isLockpick(null));
	}

	@Test
	@DisplayName("isEnabledFollowsConfig")
	void isEnabledFollowsConfig() {
		assertTrue(service.isEnabled());

		when(config.isHotwireEnabled()).thenReturn(false);

		assertFalse(service.isEnabled());
	}

	@Test
	@DisplayName("completesAfterSecondsCommitsCarTheft: 6 s of ticks in range -> grant, lockpick -1, one Car_Theft")
	void completesAfterSecondsCommitsCarTheft() {
		assertTrue(service.start(thief, car, OWNER, "sports_car"));

		ticks(STEPS - 1);
		verify(crimes, never()).commit(any(Player.class), anyString(), any(Location.class));
		assertNull(policy.grantedTo(carUuid), "not done one step early");

		ticks(1);

		assertEquals(THIEF, policy.grantedTo(carUuid));
		verify(lockpick).setAmount(2);
		verify(crimes, times(1)).commit(thief, Crimes.CAR_THEFT, carLocation);
		verify(crimes, never()).commit(any(Player.class), anyString(), any(Location.class), anyBoolean(), anyInt());
		assertFalse(service.isHotwiring(THIEF));
	}

	@Test
	@DisplayName("theftAttemptEventCarriesThiefCarAndOwner")
	void theftAttemptEventCarriesThiefCarAndOwner() {
		service.start(thief, car, OWNER, "sports_car");

		CarTheftAttemptEvent attempt = (CarTheftAttemptEvent) fired.stream()
		                                                           .filter(CarTheftAttemptEvent.class::isInstance)
		                                                           .findFirst()
		                                                           .orElseThrow();
		assertSame(thief, attempt.getThief());
		assertSame(car, attempt.getVehicle());
		assertEquals(OWNER, attempt.getOwner());
	}

	@Test
	@DisplayName("progressShownEveryStepAndDoneAtTheEnd: action bar counts down, then confirms")
	void progressShownEveryStepAndDoneAtTheEnd() {
		service.start(thief, car, OWNER, "sports_car");

		ticks(STEPS);

		actionBar.verify(() -> ActionBarManager.send(thief, "progress"), atLeastOnce());
		actionBar.verify(() -> ActionBarManager.send(thief, "done"), times(1));
		verify(messages, atLeastOnce()).progress(anyInt());
	}

	@Test
	@DisplayName("walkingAwayCancels: thief 4 blocks away at step 3 -> no grant, no crime, lockpick kept")
	void walkingAwayCancels() {
		service.start(thief, car, OWNER, "sports_car");
		ticks(2);

		thiefX = -3.0; // car at x=1 -> 4 blocks, over Max_Distance 3.0
		ticks(STEPS);

		assertNull(policy.grantedTo(carUuid));
		verify(crimes, never()).commit(any(Player.class), anyString(), any(Location.class));
		verify(lockpick, never()).setAmount(anyInt());
		assertFalse(service.isHotwiring(THIEF));
		actionBar.verify(() -> ActionBarManager.send(thief, "cancel"), times(1));
	}

	@Test
	@DisplayName("exactlyMaxDistanceStillCounts: 3.0 blocks away is inside Max_Distance 3.0")
	void exactlyMaxDistanceStillCounts() {
		service.start(thief, car, OWNER, "sports_car");
		thiefX = -2.0; // car at x=1 -> exactly 3.0

		ticks(STEPS);

		assertEquals(THIEF, policy.grantedTo(carUuid));
	}

	@Test
	@DisplayName("offlineThiefCancels: logging out mid-hotwire ends it silently, no crime, no grant")
	void offlineThiefCancels() {
		service.start(thief, car, OWNER, "sports_car");
		ticks(3);

		thiefOnline = false;
		ticks(STEPS);

		assertNull(policy.grantedTo(carUuid));
		verify(crimes, never()).commit(any(Player.class), anyString(), any(Location.class));
		assertFalse(service.isHotwiring(THIEF));
	}

	@Test
	@DisplayName("carGoneCancels: the car was picked up or destroyed mid-hotwire")
	void carGoneCancels() {
		service.start(thief, car, OWNER, "sports_car");
		ticks(3);

		carDead = true;
		ticks(STEPS);

		assertNull(policy.grantedTo(carUuid));
		verify(crimes, never()).commit(any(Player.class), anyString(), any(Location.class));
		assertFalse(service.isHotwiring(THIEF));
	}

	@Test
	@DisplayName("otherWorldCancels: thief teleported to another world is out of range, not an exception")
	void otherWorldCancels() {
		service.start(thief, car, OWNER, "sports_car");
		World elsewhere = mock(World.class);
		when(thief.getLocation()).thenAnswer(invocation -> new Location(elsewhere, 0, 64, 0));

		ticks(STEPS);

		assertNull(policy.grantedTo(carUuid));
		verify(crimes, never()).commit(any(Player.class), anyString(), any(Location.class));
	}

	@Test
	@DisplayName("cancelledTheftAttemptNeverStarts: a listener cancelling CarTheftAttemptEvent means no timer")
	void cancelledTheftAttemptNeverStarts() {
		cancelTheftAttempt = true;

		assertFalse(service.start(thief, car, OWNER, "sports_car"));
		ticks(STEPS);

		assertFalse(service.isHotwiring(THIEF));
		assertNull(policy.grantedTo(carUuid));
		verify(crimes, never()).commit(any(Player.class), anyString(), any(Location.class));
		verify(lockpick, never()).setAmount(anyInt());
	}

	@Test
	@DisplayName("disabledNeverStartsAndFiresNoEvent: Hotwire.Enable false")
	void disabledNeverStartsAndFiresNoEvent() {
		when(config.isHotwireEnabled()).thenReturn(false);

		assertFalse(service.start(thief, car, OWNER, "sports_car"));

		assertTrue(fired.isEmpty());
		assertFalse(service.isHotwiring(THIEF));
	}

	@Test
	@DisplayName("startWithoutLockpickInHandRefused: tells the player, fires no event")
	void startWithoutLockpickInHandRefused() {
		when(inventory.getItemInMainHand()).thenReturn(mock(ItemStack.class));

		assertFalse(service.start(thief, car, OWNER, "sports_car"));

		assertTrue(fired.isEmpty());
		actionBar.verify(() -> ActionBarManager.send(thief, "no-lockpick"), times(1));
	}

	@Test
	@DisplayName("lockpickGoneBeforeDoneCancels: dropped or swapped away -> no crime, no grant, no-lockpick message")
	void lockpickGoneBeforeDoneCancels() {
		service.start(thief, car, OWNER, "sports_car");
		ticks(3);

		lockpickCarried = false;
		ticks(STEPS);

		assertNull(policy.grantedTo(carUuid));
		verify(crimes, never()).commit(any(Player.class), anyString(), any(Location.class));
		actionBar.verify(() -> ActionBarManager.send(thief, "no-lockpick"), atLeastOnce());
	}

	@Test
	@DisplayName("consumeFalseKeepsLockpick: Hotwire.Consume_Lockpick false -> grant and crime, lockpick untouched")
	void consumeFalseKeepsLockpick() {
		when(config.isHotwireConsumeLockpick()).thenReturn(false);
		service.start(thief, car, OWNER, "sports_car");

		ticks(STEPS);

		assertEquals(THIEF, policy.grantedTo(carUuid));
		verify(lockpick, never()).setAmount(anyInt());
		verify(inventory, never()).setItem(anyInt(), any());
		verify(crimes, times(1)).commit(thief, Crimes.CAR_THEFT, carLocation);
	}

	@Test
	@DisplayName("lastLockpickIsRemovedFromTheSlot: amount 1 -> slot emptied, not set to 0")
	void lastLockpickIsRemovedFromTheSlot() {
		when(lockpick.getAmount()).thenReturn(1);
		service.start(thief, car, OWNER, "sports_car");

		ticks(STEPS);

		verify(inventory).setItem(0, null);
		verify(lockpick, never()).setAmount(anyInt());
	}

	@Test
	@DisplayName("exactlyOneCrimePerSuccess: ticking long after completion never commits again")
	void exactlyOneCrimePerSuccess() {
		service.start(thief, car, OWNER, "sports_car");

		ticks(STEPS * 3);

		verify(crimes, times(1)).commit(any(Player.class), anyString(), any(Location.class));
	}

	@Test
	@DisplayName("secondStartOnSameCarOrBySameThiefIgnored: one hotwire per car and per thief")
	void secondStartIgnored() {
		assertTrue(service.start(thief, car, OWNER, "sports_car"));

		Player rival = mock(Player.class);
		UUID   rivalId = UUID.randomUUID();
		when(rival.getUniqueId()).thenReturn(rivalId);
		when(rival.getInventory()).thenReturn(inventory);

		assertFalse(service.start(rival, car, OWNER, "sports_car"), "car already being hotwired");
		assertFalse(service.start(thief, car, OWNER, "sports_car"), "thief already hotwiring");
		assertFalse(service.isHotwiring(rivalId));
	}

	@Test
	@DisplayName("stolenByMarkedOnVehiclePdc: gadget stolen_by = thief uuid string, and nothing else is written")
	void stolenByMarkedOnVehiclePdc() {
		service.start(thief, car, OWNER, "sports_car");

		ticks(STEPS);

		ArgumentCaptor<NamespacedKey> key = ArgumentCaptor.forClass(NamespacedKey.class);
		verify(carPdc, times(1)).set(key.capture(), eq(PersistentDataType.STRING), eq(THIEF.toString()));
		assertTrue(key.getValue().getKey().contains("stolen_by"));
		verify(carPdc, times(1)).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
	}

	@Test
	@DisplayName("ownershipNeverTransfers: after hotwire the thief still cannot use or pick up, grant only mounts (T-73)")
	void ownershipNeverTransfers() {
		service.start(thief, car, OWNER, "sports_car");

		ticks(STEPS);

		Player thiefAgain = mock(Player.class);
		when(thiefAgain.getUniqueId()).thenReturn(THIEF);
		assertFalse(policy.canUse(thiefAgain, OWNER));
		assertTrue(policy.canMount(thiefAgain, OWNER, carUuid));
	}

	@Test
	@DisplayName("secondsAreReadAtStartSoReloadApplies: a reloaded Hotwire.Seconds shortens the next hotwire")
	void secondsReadAtStart() {
		when(config.getHotwireSeconds()).thenReturn(2);
		service.start(thief, car, OWNER, "sports_car");

		ticks(4); // 2 s = 4 steps

		assertEquals(THIEF, policy.grantedTo(carUuid));
	}

	@Test
	@DisplayName("startSchedulesOneRepeatingTaskEvery10Ticks: second concurrent hotwire reuses the same timer")
	void startSchedulesOneRepeatingTask() {
		service.start(thief, car, OWNER, "sports_car");

		Player second = mock(Player.class);
		when(second.getUniqueId()).thenReturn(UUID.randomUUID());
		when(second.isOnline()).thenReturn(true);
		when(second.getInventory()).thenReturn(inventory);
		when(second.getLocation()).thenReturn(new Location(world, 50, 64, 0));
		Entity otherCar = mock(Entity.class);
		when(otherCar.getUniqueId()).thenReturn(UUID.randomUUID());
		when(otherCar.getLocation()).thenReturn(new Location(world, 51, 64, 0));
		when(otherCar.isValid()).thenReturn(true);
		when(otherCar.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
		service.start(second, otherCar, OWNER, "van");

		verify(bukkit.scheduler(), times(1)).runTaskTimer(eq(plugin), any(Runnable.class), eq(10L), eq(10L));
	}

	@Test
	@DisplayName("crimeCommittedAtCarLocationNotThiefLocation: the witness gate looks at the car")
	void crimeAtCarLocation() {
		service.start(thief, car, OWNER, "sports_car");

		ticks(STEPS);

		ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
		verify(crimes).commit(eq(thief), eq(Crimes.CAR_THEFT), where.capture());
		assertEquals(1.0, where.getValue().getX(), 1e-9);
	}

	@Test
	@DisplayName("nullOwnerStillAllowed: event carries a null owner when the placer is unknown")
	void nullOwnerStillAllowed() {
		assertTrue(service.start(thief, car, null, "sports_car"));

		CarTheftAttemptEvent attempt = (CarTheftAttemptEvent) fired.get(0);
		assertNull(attempt.getOwner());
	}

	@Test
	@DisplayName("tickWithNoHotwiresIsHarmless")
	void tickWithNoHotwiresIsHarmless() {
		service.tick();
		verify(bukkit.scheduler(), never()).runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(),
		                                                  anyLong());
	}
}
