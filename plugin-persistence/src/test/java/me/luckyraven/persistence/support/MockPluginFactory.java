package me.luckyraven.persistence.support;

import me.luckyraven.persistence.database.DatabaseSettingsProvider;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Factory helpers for producing pre-configured {@link JavaPlugin} and {@link DatabaseSettingsProvider} mocks used
 * across all integration tests.
 */
public final class MockPluginFactory {

	private MockPluginFactory() { }

	/**
	 * Creates a {@link JavaPlugin} mock whose {@code getDataFolder()} returns the supplied {@code dataFolder} path.
	 *
	 * <p>Notably {@code isEnabled()} returns {@code false} so that
	 * {@code DatabaseHelper.runQueriesAsync()} takes the synchronous fallback path - no Bukkit scheduler interaction is
	 * needed.
	 *
	 * <p>{@code getResource()} always returns {@code null} so {@code FileHandler.create(false)}
	 * falls through to a plain {@code file.createNewFile()} without looking for JAR resources.
	 */
	public static JavaPlugin plugin(Path dataFolder) {
		JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());

		// PluginDescriptionFile has a public (name, version, main) constructor.
		PluginDescriptionFile desc = new PluginDescriptionFile("TestPlugin", "0.7.3-DEV", "me.test.Main");
		when(plugin.getDescription()).thenReturn(desc);

		when(plugin.getResource(anyString())).thenReturn(null);
		doNothing().when(plugin).saveResource(anyString(), anyBoolean());

		// Forces DatabaseHelper.runQueriesAsync() → synchronous execution
		when(plugin.isEnabled()).thenReturn(false);
		return plugin;
	}

	/**
	 * Settings that will cause MySQL initialisation to fail immediately (no driver on classpath) and then fall back to
	 * SQLite.
	 */
	public static DatabaseSettingsProvider mysqlWithSQLiteFallback() {
		DatabaseSettingsProvider s = mock(DatabaseSettingsProvider.class);
		when(s.getMysqlHost()).thenReturn("127.0.0.1");
		when(s.getMysqlPort()).thenReturn(1);            // port that refuses connections fast
		when(s.getMysqlUsername()).thenReturn("invalid");
		when(s.getMysqlPassword()).thenReturn("invalid");
		when(s.isSqliteFailedMysql()).thenReturn(true);  // enables fallback
		when(s.isSqliteBackup()).thenReturn(false);
		return s;
	}

	/**
	 * Settings that will cause MySQL initialisation to fail with no fallback. After {@code setType(MYSQL)},
	 * {@code handler.getDatabase()} will be {@code null}.
	 */
	public static DatabaseSettingsProvider mysqlNoFallback() {
		DatabaseSettingsProvider s = mock(DatabaseSettingsProvider.class);
		when(s.getMysqlHost()).thenReturn("127.0.0.1");
		when(s.getMysqlPort()).thenReturn(1);
		when(s.getMysqlUsername()).thenReturn("invalid");
		when(s.getMysqlPassword()).thenReturn("invalid");
		when(s.isSqliteFailedMysql()).thenReturn(false); // NO fallback
		when(s.isSqliteBackup()).thenReturn(false);
		return s;
	}

	/**
	 * Settings for a basic SQLite-only setup.
	 */
	public static DatabaseSettingsProvider sqliteOnly() {
		DatabaseSettingsProvider s = mock(DatabaseSettingsProvider.class);
		when(s.isSqliteFailedMysql()).thenReturn(false);
		when(s.isSqliteBackup()).thenReturn(false);
		return s;
	}

	/**
	 * Settings with SQLite backup enabled.
	 */
	public static DatabaseSettingsProvider sqliteWithBackup() {
		DatabaseSettingsProvider s = mock(DatabaseSettingsProvider.class);
		when(s.isSqliteFailedMysql()).thenReturn(false);
		when(s.isSqliteBackup()).thenReturn(true);
		return s;
	}

	/**
	 * Deletes all SQLite database files (*.db, *-wal, *-shm) in the given directory, retrying up to 20 times with 50 ms
	 * pauses between attempts.
	 *
	 * <p>On Windows, HikariCP background threads may briefly hold OS file handles after
	 * {@code dataSource.close()} returns. Call this at the end of {@code @AfterEach} - before JUnit's {@code @TempDir}
	 * extension runs its own cleanup - to avoid the {@code "Failed to delete temp directory"} error.
	 */
	public static void releaseDbFiles(Path dir) {
		for (int attempt = 0; attempt < 20; attempt++) {
			boolean anyLocked = false;
			try (Stream<Path> stream = Files.walk(dir)) {
				for (Path p : (Iterable<Path>) stream::iterator) {
					String name = p.getFileName().toString();
					if (!(name.endsWith(".db") || name.endsWith("-wal") || name.endsWith("-shm"))) continue;

					try {
						Files.deleteIfExists(p);
					} catch (IOException e) {
						anyLocked = true;
					}
				}
			} catch (IOException ignored) { }
			if (!anyLocked) return;
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}
}
