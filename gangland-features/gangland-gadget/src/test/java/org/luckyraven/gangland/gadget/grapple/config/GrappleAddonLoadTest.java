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
			   Pull_Acceleration: 0.04
			   Reel_Speed: 0.5
			   Min_Rope_Length: 2.5
			   Arrival_Distance: 2.0
			   Cooldown_Seconds: 5
			   Max_Duration_Ticks: 60
			   Fall_Damage_Grace_Ticks: 30
			   Require_Line_Of_Sight: false
			   Shot_Speed: 2.5
			   Miss_Cooldown_Ticks: 0
			   Fire_Sound: "ENTITY_ARROW_SHOOT"
			   Attach_Sound: ""

			missing_display_name:
			   Material: FISHING_ROD

			missing_material:
			   Display_Name: "&bNo Material"

			defaults_only:
			   Material: FISHING_ROD
			   Display_Name: "&bDefaults Only"

			zero_arrival_distance:
			   Material: FISHING_ROD
			   Display_Name: "&bZero Arrival Distance"
			   Arrival_Distance: 0

			out_of_range:
			   Material: FISHING_ROD
			   Display_Name: "&bOut Of Range"
			   Max_Distance: 64
			   Shot_Speed: 0
			   Max_Pull_Speed: 0
			   Pull_Acceleration: 0
			   Reel_Speed: 0
			   Min_Rope_Length: 0

			out_of_range_high:
			   Material: FISHING_ROD
			   Display_Name: "&bOut Of Range High"
			   Shot_Speed: 10
			   Max_Pull_Speed: 10
			   Max_Duration_Ticks: 200
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
		assertEquals(0.05, grapple.getPullAcceleration());
		assertEquals(0.3, grapple.getReelSpeed());
		assertEquals(3.0, grapple.getMinRopeLength());
		assertEquals(3.5, grapple.getArrivalDistance());
		assertEquals(8, grapple.getCooldownSeconds());
		assertEquals(70, grapple.getMaxDurationTicks());
		assertEquals(40, grapple.getFallDamageGraceTicks());
		assertTrue(grapple.isRequireLineOfSight());
		assertEquals(3.9, grapple.getShotSpeed());
		assertEquals(10, grapple.getMissCooldownTicks());
		assertEquals("ITEM_CROSSBOW_SHOOT", grapple.getFireSound().sound());
		assertEquals("ENTITY_ARROW_HIT", grapple.getAttachSound().sound());
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
		assertEquals(0.04, grapple.getPullAcceleration());
		assertEquals(0.5, grapple.getReelSpeed());
		assertEquals(2.5, grapple.getMinRopeLength());
		assertEquals(2.0, grapple.getArrivalDistance());
		assertEquals(5, grapple.getCooldownSeconds());
		assertEquals(60, grapple.getMaxDurationTicks());
		assertEquals(30, grapple.getFallDamageGraceTicks());
		assertEquals(false, grapple.isRequireLineOfSight());
		assertEquals(2.5, grapple.getShotSpeed());
		assertEquals(0, grapple.getMissCooldownTicks());
		assertEquals("ENTITY_ARROW_SHOOT", grapple.getFireSound().sound());
		assertNull(grapple.getAttachSound(), "an empty sound name means silent");
		assertEquals("gangland.grapples.valid_grapple", grapple.getPermission());
	}

	@Test
	@DisplayName("fix round 1 minor: Arrival_Distance: 0 is clamped to 0.1, not loaded as 0 (0 -> normalize() NaN)")
	void zeroArrivalDistance_clampedToPositiveFloor() {
		GrappleAddon addon = addon();
		addon.loadGrapples(loadYaml(YAML));

		Grapple grapple = addon.getGrapple("zero_arrival_distance");
		assertNotNull(grapple);

		assertEquals(0.1, grapple.getArrivalDistance());
	}

	@Test
	@DisplayName("too-low values clamp up: Max_Distance to 30, Shot_Speed 0.5, speeds/acceleration/rope to their floors")
	void outOfRange_clamped() {
		GrappleAddon addon = addon();
		addon.loadGrapples(loadYaml(YAML));

		Grapple grapple = addon.getGrapple("out_of_range");
		assertNotNull(grapple);

		assertEquals(30, grapple.getMaxDistance());
		assertEquals(0.5, grapple.getShotSpeed());
		assertEquals(0.1, grapple.getMaxPullSpeed());
		assertEquals(0.01, grapple.getPullAcceleration());
		assertEquals(0.01, grapple.getReelSpeed());
		assertEquals(0.5, grapple.getMinRopeLength());
	}

	@Test
	@DisplayName("too-high values clamp down: Shot_Speed and Max_Pull_Speed to 3.9 b/t, Max_Duration_Ticks to 79")
	void outOfRangeHigh_clamped() {
		GrappleAddon addon = addon();
		addon.loadGrapples(loadYaml(YAML));

		Grapple grapple = addon.getGrapple("out_of_range_high");
		assertNotNull(grapple);

		assertEquals(3.9, grapple.getShotSpeed(), "the client clamps a velocity packet to 3.9 b/t per axis");
		assertEquals(3.9, grapple.getMaxPullSpeed());
		assertEquals(79, grapple.getMaxDurationTicks(), "80+ ticks airborne kicks on allow-flight=false servers");
	}
}
