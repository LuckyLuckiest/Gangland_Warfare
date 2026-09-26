package org.luckyraven.gangland.gadget.car;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.car.vehicle.ParkedVehicle;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleMovementTask;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleRegistry;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleSession;
import org.luckyraven.gangland.gadget.car.vehicle.entity.VehicleEntity;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.item.fuel.FuelService;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins three CarService defects surfaced by testserver wave 1 (cluster "gadget"):
 * <ul>
 *   <li>gi=50 (P1) — {@code pickupCar}/{@code destroyCar} discard {@code addItem}'s overflow map, so a full
 *       inventory silently destroys the car item after the parked entity/DB row are already gone.</li>
 *   <li>gi=51 (P2) — {@code parkCar}/{@code forcePark}/{@code destroyAll} persist {@code session.getDriverUUID()}
 *       as the car's placer, so any drive by someone other than the owner (gang mate, bypass-permission holder)
 *       silently reassigns ownership.</li>
 *   <li>gi=53 (P2) — {@code destroyAll}'s already-parked pass despawns and clears {@code parkedCarRecords} without
 *       saving them first, so a park queued just before shutdown (still async while enabled) can be cancelled by
 *       {@code Server.getScheduler().cancelTasks} with no synchronous fallback.</li>
 * </ul>
 */
@DisplayName("CarService — item-loss, ownership and shutdown-persistence defects")
class CarServiceTest {

	private static final UUID OWNER = UUID.randomUUID();

	private VehicleRegistry        vehicleRegistry;
	@SuppressWarnings("unchecked")
	private IRepository<ParkedCar> repository = mock(IRepository.class);
	private CarService              carService;
	private BukkitStatics           bukkit;

	@BeforeEach
	void setUp() {
		BukkitRegistryFixture.install();

		vehicleRegistry = new VehicleRegistry();
		repository       = mock(IRepository.class);

		var plugin = mock(org.bukkit.plugin.java.JavaPlugin.class);
		when(plugin.getName()).thenReturn("Gangland");

		carService = new CarService(mock(CarManager.class), vehicleRegistry, plugin, repository,
		                            mock(FuelService.class), mock(GadgetPhysicsConfig.class));
	}

	@AfterEach
	void tearDown() {
		if (bukkit != null) bukkit.close();
	}

	private static Car car() {
		return Car.builder().carId("car").itemMaterial(Material.MINECART).build();
	}

	// ------------------------------------------------------------------
	// gi=50 — item loss on full inventory
	// ------------------------------------------------------------------

	@Test
	@DisplayName("pickupCar drops the car item instead of losing it when the player's inventory is full")
	void pickupCar_fullInventory_dropsLeftoverInsteadOfLosingIt() {
		UUID          entityUUID = UUID.randomUUID();
		VehicleEntity entity     = mock(VehicleEntity.class);
		carService.getParkedVehicles()
		          .put(entityUUID, new ParkedVehicle(entity, car(), OWNER, 0, 0, 100, null));

		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		World           world     = mock(World.class);
		Location        location  = mock(Location.class);
		when(player.getInventory()).thenReturn(inventory);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(location);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());

		ItemStack overflow = mock(ItemStack.class);
		when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>(Map.of(0, overflow)));

		carService.pickupCar(player, entityUUID);

		verify(world).dropItemNaturally(location, overflow);
	}

	@Test
	@DisplayName("destroyCar(returnItem=true) drops the leftover instead of losing it when the driver's inventory is full")
	void destroyCar_returnItem_fullInventory_dropsLeftoverInsteadOfLosingIt() {
		UUID   entityUUID = UUID.randomUUID();
		Player driver     = activeDriver(entityUUID, 100, 0);

		PlayerInventory inventory = mock(PlayerInventory.class);
		World           world     = mock(World.class);
		Location        location  = mock(Location.class);
		when(driver.getInventory()).thenReturn(inventory);
		when(driver.getWorld()).thenReturn(world);
		when(driver.getLocation()).thenReturn(location);
		when(driver.isOnline()).thenReturn(true);

		ItemStack overflow = mock(ItemStack.class);
		when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>(Map.of(0, overflow)));

		carService.destroyCar(entityUUID, true);

		verify(world).dropItemNaturally(location, overflow);
	}

	// ------------------------------------------------------------------
	// gi=51 — driving another's car must not transfer ownership
	// ------------------------------------------------------------------

	@Test
	@DisplayName("parkCar persists the original placer, not the current driver, as the car's owner")
	void parkCar_preservesOriginalPlacer_notCurrentDriver() {
		UUID entityUUID = UUID.randomUUID();
		// A gang mate (driverUUID != OWNER) drives OWNER's car and parks it.
		activeDriver(entityUUID, 100, 0, UUID.randomUUID());

		Location loc = mock(Location.class);
		when(loc.getWorld()).thenReturn(mock(World.class));
		VehicleSession session = vehicleRegistry.getByEntity(entityUUID);
		session.setLastKnownLocation(loc);

		carService.parkCar(entityUUID);

		ParkedVehicle parked = carService.getParkedVehicle(entityUUID);
		assertEquals(OWNER, parked.getPlacerUUID(), "parkCar must keep the original placer, not the driver");
	}

	// ------------------------------------------------------------------
	// gi=53 — destroyAll must persist already-parked records before clearing them
	// ------------------------------------------------------------------

	@Test
	@DisplayName("destroyAll saves every already-parked record before clearing them")
	void destroyAll_savesParkedRecordsBeforeClearing() {
		UUID          entityUUID = UUID.randomUUID();
		VehicleEntity entity     = mock(VehicleEntity.class);
		ParkedCar     record = new ParkedCar("db-1", "car", "world", 0, 0, 0, 0f, 0, 0, 100, OWNER, null);
		carService.getParkedVehicles().put(entityUUID, new ParkedVehicle(entity, car(), OWNER, 0, 0, 100, null));
		carService.getParkedCarRecords().put(entityUUID, record);

		carService.destroyAll();

		verify(repository).save(record);
	}

	/**
	 * Registers an active {@link VehicleSession} driven by {@code driverUUID} for a car originally placed by
	 * {@link #OWNER}, mirroring {@code CarService.mountCar}'s post-mount state.
	 */
	private Player activeDriver(UUID entityUUID, int durability, int fuel, UUID driverUUID) {
		bukkit = BukkitStatics.install();
		bukkit.statics()
		      .when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
		      .thenReturn(mock(BossBar.class));
		// buildReturnItem() builds an ItemStack via ItemBuilder, which routes through Bukkit.getItemFactory();
		// BukkitStatics intercepts every Bukkit static call, so it needs its own stub (a meta-less mock is enough —
		// ItemBuilder treats a null ItemMeta as a no-op, same as BukkitRegistryFixture's proxy factory).
		bukkit.statics().when(Bukkit::getItemFactory).thenReturn(mock(org.bukkit.inventory.ItemFactory.class));

		VehicleEntity entity = mock(VehicleEntity.class);
		when(entity.getEntityUUID()).thenReturn(entityUUID);

		Player driver = mock(Player.class);
		when(driver.getUniqueId()).thenReturn(driverUUID);

		// OWNER is the original placer; driverUUID is whoever is currently driving — mirrors the state
		// CarService.mountCar produces (placer carried over from the ParkedVehicle, driver from the mounting player).
		VehicleSession session = new VehicleSession(entity, car(), driver, OWNER, durability, fuel, 0,
		                                            ExhaustSide.LEFT);
		session.setTask(mock(VehicleMovementTask.class));
		vehicleRegistry.register(session);

		return driver;
	}

	private Player activeDriver(UUID entityUUID, int durability, int fuel) {
		return activeDriver(entityUUID, durability, fuel, UUID.randomUUID());
	}
}
