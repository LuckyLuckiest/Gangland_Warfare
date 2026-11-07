package org.luckyraven.gangland.gadget.car.config;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mirrors {@code grapple.config.GrappleAddonLoadTest}: the Material key must name a real material, otherwise the car
 * is skipped with a warning (T-38) — it must not silently load as a minecart.
 */
@DisplayName("CarAddon — initialize (an unrecognised Material skips the entry)")
class CarAddonLoadTest {

	private static final String YAML = """
			valid_car:
			   Material: MINECART
			   Display_Name: "&bValid Car"

			typo_car:
			   Material: NOT_A_REAL_MATERIAL_XYZ
			   Display_Name: "&bTypo Car"
			""";

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();

		// See GrappleAddonLoadTest: XMaterial's class init parses the MC version out of Bukkit.getVersion().
		when(Bukkit.getServer().getVersion()).thenReturn("git-Spigot-abcdef (MC: 1.21.11)");
	}

	private static CarAddon loadedAddon() throws InvalidConfigurationException {
		YamlConfiguration config = new YamlConfiguration();
		config.loadFromString(YAML);

		FileManager fileManager = mock(FileManager.class);
		FileHandler fileHandler = mock(FileHandler.class);
		when(fileManager.getFile("cars")).thenReturn(fileHandler);
		when(fileHandler.getFileConfiguration()).thenReturn(config);

		CarAddon addon = new CarAddon(key -> {}, fileManager, null);
		addon.initialize();
		return addon;
	}

	@Test
	@DisplayName("a car with a recognised Material loads with that material")
	void recognisedMaterial_loads() throws InvalidConfigurationException {
		CarAddon addon = loadedAddon();

		assertNotNull(addon.getCar("valid_car"));
		assertEquals(Material.MINECART, addon.getCar("valid_car").getItemMaterial());
	}

	@Test
	@DisplayName("a car whose Material is not a real material is skipped, not loaded as a minecart")
	void unrecognisedMaterial_skipped() throws InvalidConfigurationException {
		CarAddon addon = loadedAddon();

		assertNull(addon.getCar("typo_car"), "an unrecognised Material must skip the car, not default to MINECART");
	}
}
