package org.luckyraven.gangland.gadget.jetpack.config;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.bartizan.api.BartizanApi;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.jetpack.Jetpack;
import org.luckyraven.gangland.item.fuel.FuelKey;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WS7-D4 guard (mirrors the B7-ruled {@code CarMeleeWeaponLookupGuardTest} shape — the observable is a value, not
 * "non-invocation" of a mock, since {@code JetpackBartizanTraitBridge.register} is a static call with no instance
 * to verify against): with {@code Settings.isBartizanAvailable()} stubbed {@code false}, a jetpack entry that
 * configures {@code Bartizan_Traits:} still ends up with {@code bartizanRegistered == false} and
 * {@link Jetpack#buildItem()} stamps no {@code wearable} NBT tag.
 *
 * <p>Goes through {@link JetpackAddon#loadJetpacks} (a fixture, not a narrower {@code Jetpack.builder()} unit) so
 * the actual integration between {@code JetpackAddon} and {@code Settings.isBartizanAvailable()} is exercised, not
 * just the downstream {@code Jetpack} field.
 *
 * <p>Review fix I1 (G2-G3-review.md): {@code JetpackBartizanTraitBridge.register} now returns {@code boolean} —
 * {@code false} on its {@code rsp == null} early return — and {@code JetpackAddon} assigns {@code bartizanRegistered}
 * from that result, never from the guard condition alone. {@link #bartizanAvailableButServiceMissing_noFlagNoTag()}
 * is the previously-dropped second case this closes: the guard alone being {@code true} is no longer sufficient to
 * stamp the tag.
 */
@DisplayName("JetpackBartizanTraitBridge — WS7-D4 guard (Bartizan unavailable)")
class JetpackBartizanTraitBridgeGuardTest {

	private static final String YAML = """
			jetpack:
			   Material: IRON_CHESTPLATE
			   Name: "&bJetpack"
			   Fuel_Key: "gasoline"
			   Max_Fuel: 3600
			   Bartizan_Traits:
			      Base_Damage_Reduction: 0.05
			      Traits:
			         REINFORCED: 1
			         LIGHTWEIGHT: 2
			""";

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
		when(Bukkit.getServer().getVersion()).thenReturn("git-Spigot-abcdef (MC: 1.21.11)");
	}

	private RecordingNbtAccessor nbt;

	@BeforeEach
	void setUp() {
		nbt = new RecordingNbtAccessor();
		NbtBridge.install(nbt);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	@Test
	@DisplayName("Bartizan unavailable: bartizanRegistered stays false and buildItem() stamps no wearable tag, "
	             + "even though Bartizan_Traits is configured — the built item is still a correct, factory-fresh "
	             + "jetpack (I2: JETPACK_ID stamped, FUEL_CURRENT == Max_Fuel)")
	void bartizanUnavailable_noRegistrationNoTag() {
		stubBartizanUnavailable();

		FileManager fileManager = mock(FileManager.class);
		FileHandler fileHandler = mock(FileHandler.class);
		when(fileManager.getFile("jetpacks")).thenReturn(fileHandler);

		JetpackAddon addon = new JetpackAddon(key -> {}, fileManager, null);
		addon.loadJetpacks(loadYaml(YAML));

		Jetpack jetpack = addon.getJetpack("jetpack");
		assertNotNull(jetpack);
		assertFalse(jetpack.isBartizanRegistered());

		ItemStack stack = jetpack.buildItem();
		ItemBuilder builder = new ItemBuilder(stack);
		assertFalse(builder.hasNBTTag("wearable"), "no wearable tag when Bartizan was unavailable at load time");

		// I2: the refresher's whole contract — buildItem() must still stamp identity and a full, factory-fresh
		// fuel load, independent of the Bartizan-registration outcome above.
		assertTrue(Jetpack.isJetpackItem(stack));
		assertEquals("jetpack", Jetpack.getJetpackId(stack));
		assertEquals(3600, builder.getIntegerTagData(FuelKey.FUEL_CURRENT.getKey()), "FUEL_CURRENT == Max_Fuel");
		assertEquals(3600, builder.getIntegerTagData(FuelKey.FUEL_MAX.getKey()));
	}

	@Test
	@DisplayName("I1: Bartizan available but BartizanApi is not registered on the ServicesManager - "
	             + "bartizanRegistered stays false and buildItem() stamps no wearable tag (the guard alone is not "
	             + "enough; the bridge's own return value gates the flag)")
	void bartizanAvailableButServiceMissing_noFlagNoTag() {
		stubBartizanAvailableNoService();

		FileManager fileManager = mock(FileManager.class);
		FileHandler fileHandler = mock(FileHandler.class);
		when(fileManager.getFile("jetpacks")).thenReturn(fileHandler);

		JetpackAddon addon = new JetpackAddon(key -> {}, fileManager, null);
		addon.loadJetpacks(loadYaml(YAML));

		Jetpack jetpack = addon.getJetpack("jetpack");
		assertNotNull(jetpack);
		assertFalse(jetpack.isBartizanRegistered(),
		            "the guard alone (isBartizanAvailable() == true) must not set the flag - the bridge's own "
		            + "boolean return (false here, no BartizanApi registration found) does");

		ItemStack stack = jetpack.buildItem();
		assertFalse(new ItemBuilder(stack).hasNBTTag("wearable"),
		            "no wearable tag when the bridge could not actually reach a registered BartizanApi");
	}

	private static void stubBartizanUnavailable() {
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.isPluginEnabled("Bartizan")).thenReturn(false);
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);
	}

	private static void stubBartizanAvailableNoService() {
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.isPluginEnabled("Bartizan")).thenReturn(true);
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);

		// No stub on getRegistration(BartizanApi.class) - a bare mock's unstubbed object-returning call answers
		// null by default, exactly like Bukkit.getServicesManager().getRegistration(...) does for real when
		// nothing has registered that service.
		ServicesManager servicesManager = mock(ServicesManager.class);
		when(Bukkit.getServer().getServicesManager()).thenReturn(servicesManager);
	}

	private static YamlConfiguration loadYaml(String yaml) {
		YamlConfiguration config = new YamlConfiguration();
		try {
			config.loadFromString(yaml);
		} catch (InvalidConfigurationException exception) {
			throw new IllegalStateException(exception);
		}
		return config;
	}
}
