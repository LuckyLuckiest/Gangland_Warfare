package org.luckyraven.gangland.gadget.jetpack;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.item.fuel.FuelKey;
import org.luckyraven.gangland.item.fuel.FuelService;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins WS7 G4 part 1: a pre-0.9.2 jetpack that still carries Bartizan's old {@code wearable} identity tag (no
 * {@code jetpack} tag yet) gets re-stamped with {@link JetpackKey#JETPACK_ID} on next equip, with its fuel level
 * preserved rather than reset to the catalogue's {@code Max_Fuel} — see
 * {@code JetpackService#migrateLegacyJetpack}, called from {@link JetpackService#scheduleChestplateCheck}. Also
 * pins the two no-op cases (review M2): a non-jetpack Bartizan wearable, and an item already migrated.
 */
@DisplayName("JetpackService — legacy Bartizan-wearable migration (WS7 G4)")
class JetpackLegacyMigrationTest {

	private static final String LEGACY_ID = "legacy_pack";

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// migrateLegacyJetpack/isJetpackItem reach Material.isAir() via an XSeries registry lookup.
		BukkitRegistryFixture.install();
	}

	@BeforeEach
	void setUp() {
		NbtBridge.install(new RecordingNbtAccessor());
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private static JetpackAddon addonWithLegacyJetpack() {
		FileManager fileManager = mock(FileManager.class);
		FileHandler fileHandler = mock(FileHandler.class);
		when(fileManager.getFile("jetpacks")).thenReturn(fileHandler);

		JetpackAddon addon = new JetpackAddon(key -> {
		}, fileManager, null);

		Jetpack jetpack = Jetpack.builder()
		                         .jetpackId(LEGACY_ID)
		                         .displayName("&bLegacy Jetpack")
		                         .material(Material.IRON_CHESTPLATE)
		                         .fuelKey("gasoline")
		                         .maxFuel(3600) // deliberately != the 1200 stamped on the item below
		                         .build();
		addon.register(LEGACY_ID, jetpack);
		return addon;
	}

	/** A pre-0.9.2 item: Bartizan's raw "wearable" tag + fuel tags, no {@code jetpack} tag yet. */
	private static ItemStack legacyChestplate(int fuelCurrent) {
		ItemStack   stack   = new ItemStack(Material.IRON_CHESTPLATE);
		ItemBuilder builder = new ItemBuilder(stack);
		builder.addTag("wearable", LEGACY_ID);
		builder.addTag(FuelKey.FUEL_ID.getKey(), "gasoline");
		builder.addTag(FuelKey.FUEL_CURRENT.getKey(), fuelCurrent);
		builder.addTag(FuelKey.FUEL_MAX.getKey(), 3600);
		return builder.build();
	}

	/** Drives {@code scheduleChestplateCheck} synchronously against {@code chestplate}; returns the inventory mock. */
	private static PlayerInventory drive(JetpackService service, ItemStack chestplate) {
		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		Server          server    = mock(Server.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);

		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getChestplate()).thenReturn(chestplate);
		when(player.getServer()).thenReturn(server);
		when(server.getScheduler()).thenReturn(scheduler);
		doAnswer(invocation -> {
			Runnable task = invocation.getArgument(1);
			task.run();
			return null;
		}).when(scheduler).runTask(any(), any(Runnable.class));

		service.scheduleChestplateCheck(player);

		return inventory;
	}

	@Test
	@DisplayName("legacy 'wearable'-tagged chestplate is stamped with JETPACK_ID and keeps its fuel level")
	void legacyJetpack_migratedOnEquip_fuelPreserved() {
		JetpackAddon        addon         = addonWithLegacyJetpack();
		FuelService         fuelService   = mock(FuelService.class);
		JavaPlugin          plugin        = mock(JavaPlugin.class);
		GadgetPhysicsConfig physicsConfig = mock(GadgetPhysicsConfig.class);

		JetpackService service = new JetpackService(fuelService, plugin, physicsConfig, addon);

		PlayerInventory inventory = drive(service, legacyChestplate(1200));

		ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
		verify(inventory).setChestplate(captor.capture());

		ItemBuilder migrated = new ItemBuilder(captor.getValue());
		assertEquals(LEGACY_ID, migrated.getStringTagData(JetpackKey.JETPACK_ID.getKey()));
		assertEquals(1200, migrated.getIntegerTagData(FuelKey.FUEL_CURRENT.getKey()),
		            "fuel level must be preserved, not reset to Max_Fuel");
	}

	@Test
	@DisplayName("M2: a non-jetpack Bartizan wearable (no fuel tags) is left untouched")
	void nonJetpackWearable_noOp() {
		JetpackAddon        addon         = addonWithLegacyJetpack();
		FuelService         fuelService   = mock(FuelService.class);
		JavaPlugin          plugin        = mock(JavaPlugin.class);
		GadgetPhysicsConfig physicsConfig = mock(GadgetPhysicsConfig.class);

		JetpackService service = new JetpackService(fuelService, plugin, physicsConfig, addon);

		// A real Bartizan wearable key, but not a jetpack - carries no fuel tags at all.
		ItemStack stack = new ItemStack(Material.IRON_CHESTPLATE);
		new ItemBuilder(stack).addTag("wearable", "police_vest");

		PlayerInventory inventory = drive(service, stack);

		verify(inventory, never()).setChestplate(any());
	}

	@Test
	@DisplayName("M2: an already-migrated jetpack (JETPACK_ID already stamped) is left untouched")
	void alreadyMigrated_noOp() {
		JetpackAddon        addon         = addonWithLegacyJetpack();
		FuelService         fuelService   = mock(FuelService.class);
		JavaPlugin          plugin        = mock(JavaPlugin.class);
		GadgetPhysicsConfig physicsConfig = mock(GadgetPhysicsConfig.class);

		JetpackService service = new JetpackService(fuelService, plugin, physicsConfig, addon);

		ItemStack stack = legacyChestplate(1200);
		new ItemBuilder(stack).addTag(JetpackKey.JETPACK_ID.getKey(), LEGACY_ID);

		PlayerInventory inventory = drive(service, stack);

		verify(inventory, never()).setChestplate(any());
	}
}
