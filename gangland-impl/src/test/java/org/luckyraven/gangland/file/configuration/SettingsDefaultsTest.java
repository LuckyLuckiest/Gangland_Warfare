package org.luckyraven.gangland.file.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.support.SettingsFixture;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Docket T-16: a SQLite server ships with {@code Database.SQLite.Backup: true} and
 * {@code Database.MySQL.Host: localhost} by default, so every shutdown tries to back up into a MySQL DataSource
 * that was never configured, logging "Failed to create a backup ... Communications link failure". The fix is a
 * safer default — {@code Backup} must default to {@code false} so an operator has to opt in once
 * {@code Database.MySQL} actually points at a reachable server.
 */
@DisplayName("Settings defaults")
class SettingsDefaultsTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("T-16: Database.SQLite.Backup defaults to false when the SQLite section is empty")
	void initialize_emptySqliteSection_backupDefaultsFalse() throws IOException {
		SettingsFixture.write(tempDir, """
				Money_Symbol: '$'
				Database:
				  SQLite:
				""");

		SettingsFixture.initialize(tempDir);

		assertFalse(Settings.isSqliteBackup(), "Backup must default to false — a SQLite server should not try to "
				+ "back up into an unconfigured MySQL DataSource on shutdown");
	}
}
