package org.luckyraven.gangland.gadget.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GadgetSettings")
class GadgetSettingsTest {

	private YamlConfiguration module;
	private YamlConfiguration legacy;
	private GadgetSettings    settings;

	@BeforeEach
	void setUp() throws Exception {
		module = new YamlConfiguration();
		try (Reader reader = new InputStreamReader(
				getClass().getResourceAsStream("/gadget/gadget_settings.yml"), StandardCharsets.UTF_8)) {
			module.load(reader);
		}
		legacy = new YamlConfiguration();

		FileHandler moduleFile = mock(FileHandler.class);
		when(moduleFile.getFileConfiguration()).thenReturn(module);
		when(moduleFile.getDirectory()).thenReturn("gadget/gadget_settings");
		when(moduleFile.getFileType()).thenReturn(".yml");

		FileHandler legacyFile = mock(FileHandler.class);
		when(legacyFile.getFileConfiguration()).thenReturn(legacy);

		FileManager fileManager = mock(FileManager.class);
		when(fileManager.getFile("settings")).thenReturn(legacyFile);

		settings = new GadgetSettings(moduleFile, fileManager);
	}

	@Test
	@DisplayName("the shipped gadget_settings.yml carries the old code defaults")
	void shippedDefaults() {
		settings.initialize();

		assertEquals(20, settings.getJetpackThrustRampTicks());
		assertEquals(0.022, settings.getJetpackDescentAccel());
		assertEquals(-0.5, settings.getJetpackMaxDescentSpeed());
		assertEquals(0.03, settings.getJetpackHorizInfluence());
		assertEquals(0.25, settings.getJetpackMaxHorizSpeed());
		assertEquals(0.5, settings.getCarReverseSpeedRatio());
		assertEquals(3.0, settings.getCarHardBrakeMultiplier());
		assertEquals(1, settings.getCarFuelConsumePerTick());
	}

	@Test
	@DisplayName("a customised settings.yml value wins while the module file holds the default")
	void legacyWins() {
		legacy.set("Gadgets.Jetpack.Thrust_Ramp_Ticks", 40);
		legacy.set("Gadgets.Car.Hard_Brake_Multiplier", 5.0);

		settings.initialize();

		assertEquals(40, settings.getJetpackThrustRampTicks());
		assertEquals(5.0, settings.getCarHardBrakeMultiplier());
		assertEquals(0.5, settings.getCarReverseSpeedRatio());
	}

	@Test
	@DisplayName("a customised module value wins over settings.yml")
	void moduleWins() {
		module.set("Gadgets.Jetpack.Thrust_Ramp_Ticks", 10);
		legacy.set("Gadgets.Jetpack.Thrust_Ramp_Ticks", 40);

		settings.initialize();

		assertEquals(10, settings.getJetpackThrustRampTicks());
	}

}
