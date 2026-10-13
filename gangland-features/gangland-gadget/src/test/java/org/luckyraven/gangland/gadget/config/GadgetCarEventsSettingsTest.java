package org.luckyraven.gangland.gadget.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 0.20.0 keys in {@code gadget/gadget_settings.yml}: {@code Car_Events.Move_Interval_Ticks} and the {@code Hotwire}
 * block. The keys are optional: a file written before 0.20.0 must load with the code defaults.
 */
@DisplayName("GadgetSettings - Car_Events and Hotwire")
class GadgetCarEventsSettingsTest {

	private GadgetSettings settingsFor(YamlConfiguration module) {
		FileHandler moduleFile = mock(FileHandler.class);
		when(moduleFile.getFileConfiguration()).thenReturn(module);
		when(moduleFile.getDirectory()).thenReturn("gadget/gadget_settings");
		when(moduleFile.getFileType()).thenReturn(".yml");

		FileHandler legacyFile = mock(FileHandler.class);
		when(legacyFile.getFileConfiguration()).thenReturn(new YamlConfiguration());

		FileManager fileManager = mock(FileManager.class);
		when(fileManager.getFile("settings")).thenReturn(legacyFile);

		return new GadgetSettings(moduleFile, fileManager);
	}

	private YamlConfiguration shipped() throws Exception {
		YamlConfiguration module = new YamlConfiguration();
		try (Reader reader = new InputStreamReader(
				getClass().getResourceAsStream("/gadget/gadget_settings.yml"), StandardCharsets.UTF_8)) {
			module.load(reader);
		}
		return module;
	}

	@Test
	@DisplayName("shippedFileCarriesTheNewKeys: Car_Events and Hotwire are written out for the server owner")
	void shippedFileCarriesTheNewKeys() throws Exception {
		YamlConfiguration module = shipped();

		assertEquals(4, module.getInt("Car_Events.Move_Interval_Ticks", -1));
		assertTrue(module.getBoolean("Hotwire.Enable", false));
		assertEquals(6, module.getInt("Hotwire.Seconds", -1));
		assertEquals(3.0, module.getDouble("Hotwire.Max_Distance", -1), 1e-9);
		assertTrue(module.getBoolean("Hotwire.Consume_Lockpick", false));
		assertEquals("lockpick", module.getString("Hotwire.Lockpick_Item"));
	}

	@Test
	@DisplayName("shippedDefaultsLoad: gadget_settings.yml -> 4 / true / 6 / 3.0 / true / lockpick")
	void shippedDefaultsLoad() throws Exception {
		GadgetSettings settings = settingsFor(shipped());

		settings.initialize();

		assertEquals(4, settings.getCarMoveIntervalTicks());
		assertTrue(settings.isHotwireEnabled());
		assertEquals(6, settings.getHotwireSeconds());
		assertEquals(3.0, settings.getHotwireMaxDistance(), 1e-9);
		assertTrue(settings.isHotwireConsumeLockpick());
		assertEquals("lockpick", settings.getHotwireLockpickItem());
	}

	@Test
	@DisplayName("missingKeysFallBackToCodeDefaults: a pre-0.20.0 file without the blocks still gets the defaults")
	void missingKeysFallBackToCodeDefaults() {
		GadgetSettings settings = settingsFor(new YamlConfiguration());

		settings.initialize();

		assertEquals(4, settings.getCarMoveIntervalTicks());
		assertTrue(settings.isHotwireEnabled());
		assertEquals(6, settings.getHotwireSeconds());
		assertEquals(3.0, settings.getHotwireMaxDistance(), 1e-9);
		assertTrue(settings.isHotwireConsumeLockpick());
		assertEquals("lockpick", settings.getHotwireLockpickItem());
	}

	@Test
	@DisplayName("customValuesAreRead: every knob follows the file")
	void customValuesAreRead() {
		YamlConfiguration module = new YamlConfiguration();
		module.set("Car_Events.Move_Interval_Ticks", 10);
		module.set("Hotwire.Enable", false);
		module.set("Hotwire.Seconds", 12);
		module.set("Hotwire.Max_Distance", 5.5);
		module.set("Hotwire.Consume_Lockpick", false);
		module.set("Hotwire.Lockpick_Item", "crowbar");
		GadgetSettings settings = settingsFor(module);

		settings.initialize();

		assertEquals(10, settings.getCarMoveIntervalTicks());
		assertFalse(settings.isHotwireEnabled());
		assertEquals(12, settings.getHotwireSeconds());
		assertEquals(5.5, settings.getHotwireMaxDistance(), 1e-9);
		assertFalse(settings.isHotwireConsumeLockpick());
		assertEquals("crowbar", settings.getHotwireLockpickItem());
	}

	@Test
	@DisplayName("reloadPicksUpEditedValues: initialize() again re-reads the file")
	void reloadPicksUpEditedValues() {
		YamlConfiguration module = new YamlConfiguration();
		GadgetSettings    settings = settingsFor(module);
		settings.initialize();
		assertEquals(6, settings.getHotwireSeconds());

		module.set("Hotwire.Seconds", 2);
		settings.initialize();

		assertEquals(2, settings.getHotwireSeconds());
	}
}
