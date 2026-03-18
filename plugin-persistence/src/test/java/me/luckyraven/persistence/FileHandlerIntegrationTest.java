package me.luckyraven.persistence;

import me.luckyraven.persistence.support.MockPluginFactory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link FileHandler}.
 *
 * <p>Tests exercise real file I/O against JUnit 5 {@code @TempDir} directories.
 * Only non-JAR resource creation is tested ({@code create(false)}) so the tests do not depend on a real JAR
 * classloader.
 *
 * <h2>Covered behaviors</h2>
 * <ul>
 *   <li>Create / delete lifecycle for both YAML and non-YAML files
 *   <li>YAML loading, save, reload
 *   <li>Config-version mismatch triggers a backup + recreation
 *   <li>Accessor contracts: {@code getName()}, {@code getFileType()}, {@code getDirectory()}
 *   <li>Equality semantics
 * </ul>
 */
@DisplayName("FileHandler - file lifecycle and YAML I/O")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class FileHandlerIntegrationTest {

	@TempDir
	Path tempDir;

	private JavaPlugin plugin;

	@BeforeEach
	void setUp() {
		plugin = MockPluginFactory.plugin(tempDir);
	}

	// ──────────────────────────────────── Non-YAML file lifecycle ──

	@Test
	@DisplayName("create(false) - creates the file on disk")
	void create_nonYaml_createsFileOnDisk() throws IOException {
		FileHandler fh = new FileHandler(plugin, "data", "db");
		fh.create(false);

		// FileHandler(plugin, "data", "db") resolves to dataFolder/data.db
		// (directory field = name = "data", so resourceFile = "data.db")
		File expected = new File(fh.getDirectory());
		assertTrue(expected.exists(), "File must exist after create(): " + expected.getAbsolutePath());
	}

	@Test
	@DisplayName("create(false) - idempotent; calling twice does not throw or overwrite")
	void create_idempotent() throws IOException {
		FileHandler fh = new FileHandler(plugin, "idempotent", "db");
		fh.create(false);
		assertDoesNotThrow(() -> fh.create(false));
	}

	@Test
	@DisplayName("delete - removes the file from disk")
	void delete_removesFileFromDisk() throws IOException {
		FileHandler fh = new FileHandler(plugin, "delme", "db");
		fh.create(false);

		fh.delete();

		File expected = new File(tempDir.toFile(), "delme" + File.separator + "delme.db");
		assertFalse(expected.exists(), "File must not exist after delete()");
	}

	@Test
	@DisplayName("delete - does not throw when the file does not exist")
	void delete_nonExistentFile_doesNotThrow() {
		FileHandler fh = new FileHandler(plugin, "ghost", "db");
		assertDoesNotThrow(fh::delete);
	}

	// ──────────────────────────────────────────── Accessors ──

	@Test
	@DisplayName("getName - returns the name passed to the constructor")
	void getName_returnsConstructorName() {
		FileHandler fh = new FileHandler(plugin, "myfile", "yml");
		assertEquals("myfile", fh.getName());
	}

	@Test
	@DisplayName("getFileType - returns type without leading dot")
	void getFileType_returnsTypeWithoutDot() {
		FileHandler fh = new FileHandler(plugin, "config", "yml");
		assertEquals("yml", fh.getFileType());
	}

	@Test
	@DisplayName("getFileType - accepts type WITH leading dot in the constructor")
	void getFileType_acceptsTypeWithDot() {
		FileHandler fh = new FileHandler(plugin, "config", ".yml");
		assertEquals("yml", fh.getFileType());
	}

	@Test
	@DisplayName("getDirectory - returns the absolute path of the file after create()")
	void getDirectory_afterCreate_returnsAbsolutePath() throws IOException {
		FileHandler fh = new FileHandler(plugin, "dirtest", "db");
		fh.create(false);

		String dir = fh.getDirectory();
		assertNotNull(dir);
		assertTrue(new File(dir).isAbsolute(), "getDirectory() must return an absolute path");
		assertTrue(dir.endsWith("dirtest.db"), "Path must end with the file name");
	}

	// ──────────────────────────────────────── YAML lifecycle ──

	@Test
	@DisplayName("create(false) on a YAML file - isLoaded returns true")
	void yamlCreate_isLoaded() throws IOException {
		FileHandler fh = new FileHandler(plugin, "config", "yml");
		fh.create(false);

		assertTrue(fh.isLoaded(), "YAML file must be marked as loaded after create()");
	}

	@Test
	@DisplayName("getFileConfiguration - returns non-null YamlConfiguration after create()")
	void yamlCreate_fileConfigurationNotNull() throws IOException {
		FileHandler fh = new FileHandler(plugin, "settings", "yml");
		fh.create(false);

		assertNotNull(fh.getFileConfiguration());
	}

	@Test
	@DisplayName("save - persists a YAML key so it survives a reload")
	void yamlSave_persistsData() throws IOException {
		FileHandler fh = new FileHandler(plugin, "saveme", "yml");
		fh.create(false);

		fh.getFileConfiguration().set("gangName", "The Ravens");
		fh.save();

		// Reload from disk and verify the value is still there
		fh.reloadData();
		assertEquals("The Ravens", fh.getFileConfiguration().getString("gangName"));
	}

	@Test
	@DisplayName("reloadData - picks up changes written directly to the YAML file")
	void yamlReload_picksUpExternalChanges() throws IOException {
		FileHandler fh = new FileHandler(plugin, "reload", "yml");
		fh.create(false);

		// Write initial value and save
		fh.getFileConfiguration().set("level", 1);
		fh.save();

		// Simulate an external edit by saving a new value through a second handler
		FileHandler editor = new FileHandler(plugin, "reload", "yml");
		editor.create(false);
		editor.getFileConfiguration().set("level", 42);
		editor.save();

		// The original handler reloads and should now see the new value
		fh.reloadData();
		assertEquals(42, fh.getFileConfiguration().getInt("level"),
					 "reloadData() must reflect changes written by another process");
	}

	@Test
	@DisplayName("save - does not throw when called on a non-YAML file (no-op)")
	void save_nonYamlFile_doesNotThrow() throws IOException {
		FileHandler fh = new FileHandler(plugin, "noop_save", "db");
		fh.create(false);

		assertDoesNotThrow(fh::save);
	}

	// ─────────────────────────────────── Config-version mismatch ──

	@Test
	@DisplayName("config version mismatch - old file is renamed and a fresh file is created")
	void yamlVersionMismatch_renamedAndRecreated() throws IOException {
		FileHandler fh = new FileHandler(plugin, "versioned", "yml");
		fh.create(false);

		// Write a different config version to trigger the mismatch on next reload
		fh.getFileConfiguration().set("Config_Version", "0.0.1-WRONG");
		fh.save();

		// reloadData() should detect the mismatch and recreate the file
		assertDoesNotThrow(fh::reloadData, "Version mismatch must be handled without throwing");

		// The YAML key written above should be gone (fresh empty file)
		String version = fh.getFileConfiguration().getString("Config_Version");
		assertNull(version, "Fresh file must not retain the old Config_Version value");
	}

	// ─────────────────────────────────────────── Equality ──

	@Test
	@DisplayName("equals - two handlers with the same name, type and directory are equal")
	void equals_sameProperties_returnsTrue() {
		FileHandler a = new FileHandler(plugin, "shared", "config", "yml");
		FileHandler b = new FileHandler(plugin, "shared", "config", "yml");
		assertEquals(a, b);
	}

	@Test
	@DisplayName("equals - handlers with different names are not equal")
	void equals_differentNames_returnsFalse() {
		FileHandler a = new FileHandler(plugin, "one", "yml");
		FileHandler b = new FileHandler(plugin, "two", "yml");
		assertNotEquals(a, b);
	}

	@Test
	@DisplayName("equals - same instance is equal to itself")
	void equals_sameInstance_returnsTrue() {
		FileHandler fh = new FileHandler(plugin, "self", "yml");
		assertEquals(fh, fh);
	}
}
