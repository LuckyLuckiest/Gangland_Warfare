package org.luckyraven.gangland.file.configuration.wanted;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.support.SettingsFixture;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GanglandWantedSettings - the star-drop charge keys")
class GanglandWantedSettingsTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	@Test
	@DisplayName("an upgraded settings.yml without Enable or Formula reads off and the default formula")
	void delegatesToSettings() throws IOException {
		SettingsFixture.write(tempDir, """
				Money_Symbol: '$'
				Wanted:
				  Take_Money:
				    Amount: 50
				""");
		SettingsFixture.initialize(tempDir);

		GanglandWantedSettings settings = new GanglandWantedSettings();

		assertFalse(settings.isTakeMoneyEnabled());
		assertEquals("amount * multiplier ^ wanted", settings.getTakeMoneyFormula());
	}

	@Test
	@DisplayName("Enable true and a custom Formula are passed through")
	void delegatesToSettings_configured() throws IOException {
		SettingsFixture.write(tempDir, """
				Money_Symbol: '$'
				Wanted:
				  Take_Money:
				    Enable: true
				    Formula: 'amount * 2'
				""");
		SettingsFixture.initialize(tempDir);

		GanglandWantedSettings settings = new GanglandWantedSettings();

		assertTrue(settings.isTakeMoneyEnabled());
		assertEquals("amount * 2", settings.getTakeMoneyFormula());
	}
}
