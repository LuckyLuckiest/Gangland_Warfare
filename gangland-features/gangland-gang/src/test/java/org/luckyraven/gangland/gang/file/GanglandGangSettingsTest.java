package org.luckyraven.gangland.gang.file;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GanglandGangSettings} over the shipped {@code gang/gang_settings.yml}: the bundled defaults equal the old
 * {@code Settings.java} defaults, a customised legacy {@code settings.yml} value wins while the module file still holds
 * the default, and a customised module value wins over both.
 */
@DisplayName("GanglandGangSettings - gang/gang_settings.yml")
class GanglandGangSettingsTest {

	private YamlConfiguration module;
	private YamlConfiguration legacy;

	@BeforeEach
	void setUp() throws IOException, InvalidConfigurationException {
		module = new YamlConfiguration();
		legacy = new YamlConfiguration();

		try (InputStream in = getClass().getClassLoader().getResourceAsStream("gang/gang_settings.yml")) {
			assertNotNull(in, "gang/gang_settings.yml must ship in the module jar");
			module.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	private GanglandGangSettings load() {
		FileHandler moduleFile = mock(FileHandler.class);
		when(moduleFile.getFileConfiguration()).thenReturn(module);
		when(moduleFile.getDirectory()).thenReturn("gang/gang_settings");
		when(moduleFile.getFileType()).thenReturn(".yml");

		FileHandler settingsFile = mock(FileHandler.class);
		when(settingsFile.getFileConfiguration()).thenReturn(legacy);

		FileManager fileManager = mock(FileManager.class);
		when(fileManager.getFile("settings")).thenReturn(settingsFile);

		GanglandGangSettings settings = new GanglandGangSettings(moduleFile, fileManager);
		settings.initialize();
		return settings;
	}

	@Test
	@DisplayName("the shipped defaults load from the module jar resource")
	void shippedDefaults() {
		GanglandGangSettings s = load();

		assertFalse(s.isGangNameDuplicates());
		assertEquals("*", s.getGangDisplayNameChar());
		assertEquals("member", s.getGangRankHead());
		assertEquals("owner", s.getGangRankTail());
		assertEquals(0, BigDecimal.ZERO.compareTo(s.getGangInitialBalance()));
		assertEquals(0, new BigDecimal("100000").compareTo(s.getGangCreateFee()));
		assertEquals(0, new BigDecimal("100000000000").compareTo(s.getGangMaxBalance()));
		assertEquals(1_000.0, s.getGangContributionRate());
	}

	@Test
	@DisplayName("a customised settings.yml value wins while gang_settings.yml still holds the default")
	void legacyValueWins() {
		legacy.set("Gang.Rank.Tail", "boss");
		legacy.set("Gang.Name_Duplicates", true);
		legacy.set("Gang.Account.Create_Cost", 5_000);
		legacy.set("Gang.Account.Contribution_Rate", 10.0);
		legacy.set("Gang.Display_Name_Char", "#x");

		GanglandGangSettings s = load();

		assertEquals("boss", s.getGangRankTail());
		assertEquals(true, s.isGangNameDuplicates());
		assertEquals(0, new BigDecimal("5000").compareTo(s.getGangCreateFee()));
		assertEquals(10.0, s.getGangContributionRate());
		assertEquals("#", s.getGangDisplayNameChar());
		assertEquals("member", s.getGangRankHead());
	}

	@Test
	@DisplayName("a customised gang_settings.yml value wins over settings.yml")
	void moduleValueWins() {
		module.set("Rank.Tail", "capo");
		module.set("Account.Maximum_Balance", 123);
		legacy.set("Gang.Rank.Tail", "boss");
		legacy.set("Gang.Account.Maximum_Balance", 999);

		GanglandGangSettings s = load();

		assertEquals("capo", s.getGangRankTail());
		assertEquals(0, new BigDecimal("123").compareTo(s.getGangMaxBalance()));
	}
}
