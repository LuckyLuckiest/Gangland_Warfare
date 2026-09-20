package org.luckyraven.gangland.gadget.grapple.config;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.grapple.Grapple;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mirrors {@code jetpack.config.JetpackAddonLoadTest}. Grapple has no fuel/Bartizan model (WS8-D1) so the only
 * mandatory keys are {@code Material}/{@code Display_Name}, mirroring {@code car.config.CarAddon}'s check exactly
 * — everything else defaults, same as Car's Vehicle/Fuel/Repair sections.
 */
@DisplayName("GrappleAddon — loadGrapples (Material/Display_Name mandatory, field round-trip)")
class GrappleAddonLoadTest {

	private static final String YAML = """
			valid_grapple:
			   Material: FISHING_ROD
			   Display_Name: "&bTest Grapple"
			   Custom_Model_Data: 7
			   Lore:
			      - "&7Line one"
			   Max_Distance: 30
			   Max_Pull_Speed: 2.0
			   Pull_Acceleration: 0.4
			   Arrival_Distance: 2.0
			   Cooldown_Seconds: 5
			   Max_Duration_Ticks: 120
			   Fall_Damage_Grace_Ticks: 30
			   Require_Line_Of_Sight: false

			missing_display_name:
			   Material: FISHING_ROD

			missing_material:
			   Display_Name: "&bNo Material"

			defaults_only:
			   Material: FISHING_ROD
			   Display_Name: "&bDefaults Only"
			""";

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// Subject code reaches Material.isAir() via an XSeries registry lookup — see the fixture javadoc.
		BukkitRegistryFixture.install();

		// XMaterial's enum <clinit> parses "MC: 1.xx" out of Bukkit.getVersion() the first time any XMaterial
		// constant is touched (GrappleAddon.loadGrapples calls XMaterial.matchXMaterial). The fixture's own
		// server.getVersion() stub ("test") does not match that pattern and permanently poisons XMaterial's class
		// init for the rest of this fork, so it is overridden here before the first touch.
		when(Bukkit.getServer().getVersion()).thenReturn("git-Spigot-abcdef (MC: 1.21.11)");
	}

	private static GrappleAddon addon() {
		FileManager fileManager = mock(FileManager.class);
		FileHandler fileHandler = mock(FileHandler.class);
		when(fileManager.getFile("grapples")).thenReturn(fileHandler);

		return new GrappleAddon(key -> {}, fileManager, null);
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
	@DisplayName("an entry missing Display_Name is skipped, not loaded")
	void missingDisplayName_skipped() {
		GrappleAddon addon = addon();
		addon.loadGrapples(loadYaml(YAML));

		assertNull(addon.getGrapple("missing_display_name"));
	}

	@Test
	@DisplayName("an entry missing Material is skipped, not loaded")
	void missingMaterial_skipped() {
		GrappleAddon addon = addon();
		addon.loadGrapples(loadYaml(YAML));

		assertNull(addon.getGrapple("missing_material"));
	}

	@Test
	@DisplayName("an entry with only the mandatory keys loads with every mechanics knob defaulted")
	void defaultsOnly_loadsWithDefaults() {
		GrappleAddon addon = addon();
		addon.loadGrapples(loadYaml(YAML));

		Grapple grapple = addon.getGrapple("defaults_only");
		assertNotNull(grapple);

		assertEquals(25, grapple.getMaxDistance());
		assertEquals(1.8, grapple.getMaxPullSpeed());
		assertEquals(0.35, grapple.getPullAcceleration());
		assertEquals(1.5, grapple.getArrivalDistance());
		assertEquals(8, grapple.getCooldownSeconds());
		assertEquals(100, grapple.getMaxDurationTicks());
		assertEquals(40, grapple.getFallDamageGraceTicks());
		assertTrue(grapple.isRequireLineOfSight());
	}

	@Test
	@DisplayName("a complete entry round-trips every field through loadGrapples")
	void completeEntry_roundTrips() {
		GrappleAddon addon = addon();
		addon.loadGrapples(loadYaml(YAML));

		Grapple grapple = addon.getGrapple("valid_grapple");
		assertNotNull(grapple);

		assertEquals("valid_grapple", grapple.getGrappleId());
		assertEquals(Material.FISHING_ROD, grapple.getMaterial());
		assertEquals("&bTest Grapple", grapple.getDisplayName());
		assertEquals(7, grapple.getCustomModelData());
		assertEquals(30, grapple.getMaxDistance());
		assertEquals(2.0, grapple.getMaxPullSpeed());
		assertEquals(0.4, grapple.getPullAcceleration());
		assertEquals(2.0, grapple.getArrivalDistance());
		assertEquals(5, grapple.getCooldownSeconds());
		assertEquals(120, grapple.getMaxDurationTicks());
		assertEquals(30, grapple.getFallDamageGraceTicks());
		assertEquals(false, grapple.isRequireLineOfSight());
		assertEquals("gangland.grapples.valid_grapple", grapple.getPermission());
	}
}
