package org.luckyraven.gangland.file.configuration;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.support.SettingsFixture;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins GR-27: an old {@code settings.yml} that still carries {@code Gang.Display_Name_Char: ''} must not throw at
 * startup. The first character of a non-empty value is kept; an empty value falls back to {@code *}.
 */
@DisplayName("Settings - Gang.Display_Name_Char with an empty value (GR-27)")
class SettingsDisplayNameCharTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	@Test
	@DisplayName("an empty Display_Name_Char does not throw and falls back to *")
	void emptyValue_doesNotThrow_andFallsBackToStar() throws IOException {
		SettingsFixture.write(tempDir, "Gang:\n  Display_Name_Char: ''\n");

		assertDoesNotThrow(() -> SettingsFixture.initialize(tempDir));
		assertEquals("*", Settings.getGangDisplayNameChar());
	}

	@Test
	@DisplayName("a non-empty Display_Name_Char keeps only its first character")
	void multiCharacterValue_keepsFirstCharacter() throws IOException {
		SettingsFixture.write(tempDir, "Gang:\n  Display_Name_Char: '#x'\n");

		assertDoesNotThrow(() -> SettingsFixture.initialize(tempDir));
		assertEquals("#", Settings.getGangDisplayNameChar());
	}

}
