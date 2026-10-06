package org.luckyraven.gangland.npcshops.integration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.npcshops.banker.config.BankerSettings;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The banker settings provider tests: (1) shipped defaults load from the module jar resource,
 * (2) a customised legacy settings.yml value wins over the default while the module file still holds the default,
 * (3) a customised module value wins. Covers {@code getRenameFee} fallback and the four module-owned keys
 * ({@code Head_Track_Radius}, {@code Max_Health}, {@code Invulnerable}, {@code Fallback_Tier_Id}).
 */
@DisplayName("BankerSettingsImpl - banker configuration provider")
class BankerSettingsImplTest {

	private BankerSettings createSettings(YamlConfiguration banker, YamlConfiguration settings) throws Exception {
		FileHandler bankerFile = mock(FileHandler.class);
		when(bankerFile.getFileConfiguration()).thenReturn(banker);
		when(bankerFile.getDirectory()).thenReturn("npc");
		when(bankerFile.getFileType()).thenReturn(".yml");

		FileHandler settingsFile = mock(FileHandler.class);
		when(settingsFile.getFileConfiguration()).thenReturn(settings);

		FileManager fileManager = mock(FileManager.class);
		when(fileManager.getFile("banker_settings")).thenReturn(bankerFile);
		when(fileManager.getFile("settings")).thenReturn(settingsFile);
		// Mock checkFileLoaded to not throw
		fileManager.checkFileLoaded("banker_settings");

		return new BankerSettingsImpl(fileManager);
	}

	@Test
	@DisplayName("shipped defaults load from the module jar resource")
	void shippedDefaults_loadFromModuleJar() throws Exception {
		YamlConfiguration banker = new YamlConfiguration();
		YamlConfiguration settings = new YamlConfiguration();

		BankerSettings config = createSettings(banker, settings);

		// When no customization exists, shipped defaults apply
		assertEquals(8, config.getHeadTrackRadius());
		assertEquals(20.0, config.getMaxHealth());
		assertTrue(config.isInvulnerable());
		assertEquals("Basic", config.getFallbackTierId());
		assertEquals(0, new BigDecimal("1000").compareTo(config.getRenameFee()));
	}

	@Test
	@DisplayName("a legacy settings.yml value wins while the module file holds the default")
	void legacySettingsWin_whenModuleIsDefault() throws Exception {
		YamlConfiguration banker = new YamlConfiguration();
		YamlConfiguration settings = new YamlConfiguration();

		// Legacy settings.yml has a custom Rename_Fee
		settings.set("User.Bank.Rename_Fee", "250");

		BankerSettings config = createSettings(banker, settings);

		// Legacy wins while module file is absent (default)
		assertEquals(0, new BigDecimal("250").compareTo(config.getRenameFee()));
	}

	@Test
	@DisplayName("a custom module value wins over the default")
	void moduleValue_winsOverDefault() throws Exception {
		YamlConfiguration banker = new YamlConfiguration();
		YamlConfiguration settings = new YamlConfiguration();

		// Module file has a custom Rename_Fee
		banker.set("Rename_Fee", "500");

		BankerSettings config = createSettings(banker, settings);

		// Module custom value wins
		assertEquals(0, new BigDecimal("500").compareTo(config.getRenameFee()));
	}

	@Test
	@DisplayName("module value wins when both module and legacy are customised")
	void moduleValue_winsWhenBothCustomised() throws Exception {
		YamlConfiguration banker = new YamlConfiguration();
		YamlConfiguration settings = new YamlConfiguration();

		// Both files have custom Rename_Fee values
		banker.set("Rename_Fee", "500");
		settings.set("User.Bank.Rename_Fee", "250");

		BankerSettings config = createSettings(banker, settings);

		// Module custom value wins over legacy
		assertEquals(0, new BigDecimal("500").compareTo(config.getRenameFee()));
	}

	@Test
	@DisplayName("legacy settings.yml value is ignored when module file has a custom value")
	void moduleValue_winsOverLegacyValue() throws Exception {
		YamlConfiguration banker = new YamlConfiguration();
		YamlConfiguration settings = new YamlConfiguration();

		// Both have custom Rename_Fee - module should win
		banker.set("Rename_Fee", "750");
		settings.set("User.Bank.Rename_Fee", "250");

		BankerSettings config = createSettings(banker, settings);

		// Module custom value wins
		assertEquals(0, new BigDecimal("750").compareTo(config.getRenameFee()));
	}
	@Test
	@DisplayName("the shipped banker_settings.yml loads with no unknown-key issues")
	void shippedFile_hasNoUnknownKeys() throws Exception {
		org.luckyraven.keystone.persistence.config.ConfigReport parseReport =
				new org.luckyraven.keystone.persistence.config.ConfigReport();
		org.luckyraven.keystone.persistence.config.ConfigDocument document;
		try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("npc/banker_settings.yml")) {
			document = new org.luckyraven.keystone.persistence.config.ConfigParser().parse(
					java.nio.file.Path.of("banker_settings.yml"),
					new java.io.StringReader(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)),
					parseReport);
		}
		FileHandler bankerFile = mock(FileHandler.class);
		when(bankerFile.getFileConfiguration()).thenReturn(new YamlConfiguration());
		when(bankerFile.getParsedDocument()).thenReturn(document);
		FileHandler settingsFile = mock(FileHandler.class);
		when(settingsFile.getFileConfiguration()).thenReturn(new YamlConfiguration());
		FileManager fileManager = mock(FileManager.class);
		when(fileManager.getFile("banker_settings")).thenReturn(bankerFile);
		when(fileManager.getFile("settings")).thenReturn(settingsFile);

		org.luckyraven.keystone.persistence.config.ConfigReport report =
				new BankerSettingsImpl(fileManager).load();

		assertTrue(report.issues().stream().noneMatch(issue -> "config.unknown_key".equals(issue.code())),
		           () -> "unknown keys: " + report.issues());
	}

}
