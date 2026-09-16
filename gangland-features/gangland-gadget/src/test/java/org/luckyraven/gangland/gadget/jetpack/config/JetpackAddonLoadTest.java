package org.luckyraven.gangland.gadget.jetpack.config;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.jetpack.Jetpack;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins GD-12 (WS7 G2): a jetpack entry missing its mandatory {@code Fuel_Key} started a session that never flew
 * because nothing was ever wired to consume fuel — {@link JetpackAddon#loadJetpacks} now skips that entry outright
 * at load time instead of loading a half-configured jetpack. New test, proven red against the pre-guard
 * {@code JetpackAddon} (no such guard existed before this gate) — not a flip of an existing pinning test.
 */
@DisplayName("JetpackAddon — loadJetpacks (Fuel_Key mandatory, field round-trip)")
class JetpackAddonLoadTest {

	private static final String YAML = """
			valid_jetpack:
			   Material: IRON_CHESTPLATE
			   Name: "&bTest Jetpack"
			   Custom_Model_Data: 7
			   Lore:
			      - "&7Line one"
			   Fuel_Key: "gasoline"
			   Max_Fuel: 4000
			   Ascend_Power: 0.25
			   Max_Speed_Y: 0.5
			   Fuel_Consumption_Rate: 2

			missing_fuel_key:
			   Material: IRON_CHESTPLATE
			   Name: "&bNo Fuel Key"

			blank_fuel_key:
			   Material: IRON_CHESTPLATE
			   Name: "&bBlank Fuel Key"
			   Fuel_Key: ""

			missing_material:
			   Name: "&bNo Material"
			   Fuel_Key: "gasoline"

			missing_name:
			   Material: IRON_CHESTPLATE
			   Fuel_Key: "gasoline"

			zero_max_fuel:
			   Material: IRON_CHESTPLATE
			   Name: "&bZero Max Fuel"
			   Fuel_Key: "gasoline"
			   Max_Fuel: 0
			""";

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// Subject code reaches Material.isAir() via an XSeries registry lookup — see the fixture javadoc.
		BukkitRegistryFixture.install();

		// XMaterial's enum <clinit> parses "MC: 1.xx" out of Bukkit.getVersion() the first time any XMaterial
		// constant is touched (JetpackAddon.loadJetpacks calls XMaterial.matchXMaterial). The fixture's own
		// server.getVersion() stub ("test") does not match that pattern and permanently poisons XMaterial's class
		// init for the rest of this fork, so it is overridden here before the first touch.
		when(Bukkit.getServer().getVersion()).thenReturn("git-Spigot-abcdef (MC: 1.21.11)");

		// Settings.isBartizanAvailable() reads Bukkit.getPluginManager() directly; the fixture does not stub a
		// PluginManager at all, so JetpackAddon's unconditional per-entry call NPEs without this.
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.isPluginEnabled("Bartizan")).thenReturn(false);
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);
	}

	private static JetpackAddon addon() {
		FileManager fileManager = mock(FileManager.class);
		FileHandler fileHandler = mock(FileHandler.class);
		when(fileManager.getFile("jetpacks")).thenReturn(fileHandler);

		return new JetpackAddon(key -> {}, fileManager, null);
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

	@Test
	@DisplayName("an entry missing Fuel_Key is skipped, not loaded")
	void missingFuelKey_skipped() {
		JetpackAddon addon = addon();
		addon.loadJetpacks(loadYaml(YAML));

		assertNull(addon.getJetpack("missing_fuel_key"));
	}

	@Test
	@DisplayName("an entry with a blank Fuel_Key is skipped, not loaded")
	void blankFuelKey_skipped() {
		JetpackAddon addon = addon();
		addon.loadJetpacks(loadYaml(YAML));

		assertNull(addon.getJetpack("blank_fuel_key"));
	}

	@Test
	@DisplayName("an entry missing Material is skipped, not loaded")
	void missingMaterial_skipped() {
		JetpackAddon addon = addon();
		addon.loadJetpacks(loadYaml(YAML));

		assertNull(addon.getJetpack("missing_material"));
	}

	@Test
	@DisplayName("an entry missing Name is skipped, not loaded")
	void missingName_skipped() {
		JetpackAddon addon = addon();
		addon.loadJetpacks(loadYaml(YAML));

		assertNull(addon.getJetpack("missing_name"));
	}

	@Test
	@DisplayName("an entry with a non-positive Max_Fuel is skipped, not loaded (Minor 4 - buildItem must never "
	             + "ship a permanently dry jetpack)")
	void zeroMaxFuel_skipped() {
		JetpackAddon addon = addon();
		addon.loadJetpacks(loadYaml(YAML));

		assertNull(addon.getJetpack("zero_max_fuel"));
	}

	@Test
	@DisplayName("a complete entry round-trips every field through loadJetpacks")
	void completeEntry_roundTrips() {
		JetpackAddon addon = addon();
		addon.loadJetpacks(loadYaml(YAML));

		Jetpack jetpack = addon.getJetpack("valid_jetpack");
		assertNotNull(jetpack);

		assertEquals("valid_jetpack", jetpack.getJetpackId());
		assertEquals(Material.IRON_CHESTPLATE, jetpack.getMaterial());
		assertEquals("&bTest Jetpack", jetpack.getDisplayName());
		assertEquals(7, jetpack.getCustomModelData());
		assertEquals("gasoline", jetpack.getFuelKey());
		assertEquals(4000, jetpack.getMaxFuel());
		assertEquals(0.25, jetpack.getAscendPower());
		assertEquals(0.5, jetpack.getMaxSpeedY());
		assertEquals(2, jetpack.getFuelConsumptionRate());
		assertEquals("gangland.jetpacks.valid_jetpack", jetpack.getPermission());
	}
}
