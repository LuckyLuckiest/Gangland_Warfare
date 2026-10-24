package org.luckyraven.gangland.copsncrooks.integration.detainment;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.settings.RescueSettings;
import org.luckyraven.gangland.copsncrooks.detainment.settings.SurrenderSettings;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 0.21.0 fnd-origin: the new {@code Detainment.Surrender / Busted / Break_Free.Lockpick / Rescue} keys. They never
 * lived in settings.yml, so they are read straight from detainment.yml (no {@code MovedSetting} fallback) and clamped.
 */
@DisplayName("DetainmentSettings - the 0.21.0 keys")
class DetainmentSettingsTest {

	private YamlConfiguration legacy;
	private YamlConfiguration module;
	private FileManager       fileManager;

	private static YamlConfiguration shipped() {
		InputStream in = DetainmentSettingsTest.class.getClassLoader().getResourceAsStream("copsncrooks/detainment.yml");
		Objects.requireNonNull(in, "copsncrooks/detainment.yml must ship in the module jar");
		return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
	}

	private static FileHandler handler(YamlConfiguration config, String name) {
		FileHandler handler = mock(FileHandler.class);
		when(handler.getFileConfiguration()).thenReturn(config);
		when(handler.getName()).thenReturn(name);
		when(handler.getDirectory()).thenReturn("copsncrooks/" + name);
		when(handler.getFileType()).thenReturn(".yml");
		return handler;
	}

	@BeforeEach
	void setUp() {
		legacy      = new YamlConfiguration();
		module      = shipped();
		fileManager = mock(FileManager.class);
		FileHandler settingsHandler   = handler(legacy, "settings");
		FileHandler detainmentHandler = handler(module, "detainment");
		when(fileManager.getFile("settings")).thenReturn(settingsHandler);
		when(fileManager.getFile("detainment")).thenReturn(detainmentHandler);
	}

	private DetainmentSettings load() {
		DetainmentSettings settings = new DetainmentSettings(fileManager);
		settings.initialize();
		return settings;
	}

	@Test
	@DisplayName("the shipped detainment.yml gives Surrender 3/0.5/0.5/0.75/10, Busted on, Lockpick 2/0.05, Rescue 3/1")
	void newKeysDefaultFromModuleYaml() {
		DetainmentSettings settings = load();

		assertEquals(new SurrenderSettings(true, 3, 0.5, 0.5, 0.75, 10), settings.getSurrender());
		assertTrue(settings.isBustedEnabled());
		assertEquals(2, settings.getLockpickTapWeight());
		assertEquals(0.05, settings.getLockpickSnapChance());
		assertEquals(new RescueSettings(true, 3, 1), settings.getRescue());
	}

	@Test
	@DisplayName("a file that predates the keys (none of them present) reads the same defaults")
	void missingKeys_sameDefaults() {
		module = new YamlConfiguration();
		FileHandler emptyHandler = handler(module, "detainment");
		when(fileManager.getFile("detainment")).thenReturn(emptyHandler);

		DetainmentSettings settings = load();

		assertEquals(new SurrenderSettings(true, 3, 0.5, 0.5, 0.75, 10), settings.getSurrender());
		assertTrue(settings.isBustedEnabled());
		assertEquals(2, settings.getLockpickTapWeight());
		assertEquals(0.05, settings.getLockpickSnapChance());
		assertEquals(new RescueSettings(true, 3, 1), settings.getRescue());
	}

	@Test
	@DisplayName("an owner's values are read key by key")
	void overrides_areRead() {
		module.set("Detainment.Surrender.Enabled", false);
		module.set("Detainment.Surrender.Max_Stars", 5);
		module.set("Detainment.Surrender.Sentence_Multiplier", 0.25);
		module.set("Detainment.Surrender.Fine_Multiplier", 0.1);
		module.set("Detainment.Surrender.Bail_Multiplier", 0.6);
		module.set("Detainment.Surrender.Resist_Window_Seconds", 4);
		module.set("Detainment.Busted.Enabled", false);
		module.set("Detainment.Break_Free.Lockpick.Tap_Weight", 3);
		module.set("Detainment.Break_Free.Lockpick.Snap_Chance", 0.5);
		module.set("Detainment.Rescue.Enabled", false);
		module.set("Detainment.Rescue.Hold_Seconds", 6);
		module.set("Detainment.Rescue.Stars", 2);

		DetainmentSettings settings = load();

		assertEquals(new SurrenderSettings(false, 5, 0.25, 0.1, 0.6, 4), settings.getSurrender());
		assertFalse(settings.isBustedEnabled());
		assertEquals(3, settings.getLockpickTapWeight());
		assertEquals(0.5, settings.getLockpickSnapChance());
		assertEquals(new RescueSettings(false, 6, 2), settings.getRescue());
	}

	@Test
	@DisplayName("nonsense is clamped: multipliers >= 0, weight >= 1, chance 0..1, seconds >= 0, stars >= 0")
	void nonsense_isClamped() {
		module.set("Detainment.Surrender.Max_Stars", -4);
		module.set("Detainment.Surrender.Sentence_Multiplier", -1.0);
		module.set("Detainment.Surrender.Fine_Multiplier", -0.5);
		module.set("Detainment.Surrender.Bail_Multiplier", -2.0);
		module.set("Detainment.Surrender.Resist_Window_Seconds", -9);
		module.set("Detainment.Break_Free.Lockpick.Tap_Weight", 0);
		module.set("Detainment.Break_Free.Lockpick.Snap_Chance", 7.0);
		module.set("Detainment.Rescue.Hold_Seconds", -3);
		module.set("Detainment.Rescue.Stars", -1);

		DetainmentSettings settings = load();

		assertEquals(new SurrenderSettings(true, 0, 0.0, 0.0, 0.0, 0), settings.getSurrender());
		assertEquals(1, settings.getLockpickTapWeight());
		assertEquals(1.0, settings.getLockpickSnapChance());
		assertEquals(new RescueSettings(true, 0, 0), settings.getRescue());

		module.set("Detainment.Break_Free.Lockpick.Snap_Chance", -0.2);
		assertEquals(0.0, load().getLockpickSnapChance());
	}

	@Test
	@DisplayName("a settings.yml still holding the old Detainment keys has no say over the new ones")
	void legacySettingsYml_isIgnoredForTheNewKeys() {
		legacy.set("Detainment.Surrender.Max_Stars", 9);
		legacy.set("Detainment.Break_Free.Lockpick.Tap_Weight", 9);

		DetainmentSettings settings = load();

		assertEquals(3, settings.getSurrender().maxStars());
		assertEquals(2, settings.getLockpickTapWeight());
	}

	@Test
	@DisplayName("a reload picks up an edited file")
	void reload_seesTheEdit() {
		DetainmentSettings settings = load();
		module.set("Detainment.Rescue.Hold_Seconds", 8);

		settings.initialize();

		assertEquals(8, settings.getRescue().holdSeconds());
	}

	@Test
	@DisplayName("the 0.15.1 keys are untouched by the new reads")
	void oldKeys_stillRead() {
		DetainmentSettings settings = load();

		assertEquals(400, settings.getTransitDelayTicks());
		assertEquals(25, settings.getBreakFreeTapsRequired());
		assertEquals(2_500.0, settings.getBailBaseCost());
	}
}
