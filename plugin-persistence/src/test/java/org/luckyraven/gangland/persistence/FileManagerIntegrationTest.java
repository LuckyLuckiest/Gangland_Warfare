package org.luckyraven.gangland.persistence;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.persistence.support.MockPluginFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link FileManager}.
 *
 * <p>All methods are exercised against real files in a JUnit 5 {@code @TempDir}.
 * {@code JavaPlugin} is mocked; file I/O is real.
 */
@DisplayName("FileManager - file registry and I/O")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class FileManagerIntegrationTest {

	@TempDir
	Path tempDir;

	private JavaPlugin  plugin;
	private FileManager mgr;

	@BeforeEach
	void setUp() {
		plugin = MockPluginFactory.plugin(tempDir);
		mgr    = new FileManager(plugin);
	}

	// ──────────────────────────────────────── addFile / contains ──

	@Test
	@DisplayName("addFile(create=false) - registers the handler without touching the filesystem")
	void addFile_noCreate_registersWithoutCreatingFile() {
		FileHandler fh = new FileHandler(plugin, "lazy", "yml");
		mgr.addFile(fh, false);

		assertTrue(mgr.contains("lazy"));
	}

	@Test
	@DisplayName("addFile(create=true) - creates the file on disk and registers the handler")
	void addFile_withCreate_createsFileAndRegisters() {
		FileHandler fh = new FileHandler(plugin, "eager", "yml");
		mgr.addFile(fh, true);

		assertTrue(mgr.contains("eager"));
	}

	@Test
	@DisplayName("addFile - duplicate adds are ignored (idempotent)")
	void addFile_duplicate_isIgnored() {
		FileHandler fh = new FileHandler(plugin, "dup", "yml");
		mgr.addFile(fh, false);
		mgr.addFile(fh, false);   // second add of same instance

		assertEquals(1, mgr.getFiles().size(), "Duplicate add must not grow the set");
	}

	@Test
	@DisplayName("contains - returns false for a name that was never added")
	void contains_unknownName_returnsFalse() {
		assertFalse(mgr.contains("unknown"));
	}

	@Test
	@DisplayName("contains - lookup is case-insensitive")
	void contains_caseInsensitive() {
		mgr.addFile(new FileHandler(plugin, "MyFile", "yml"), false);
		assertTrue(mgr.contains("myfile"), "Lowercase lookup must succeed");
		assertTrue(mgr.contains("MYFILE"), "Uppercase lookup must succeed");
		assertTrue(mgr.contains("MyFile"), "Mixed-case lookup must succeed");
	}

	// ────────────────────────────────────────────── getFile ──

	@Test
	@DisplayName("getFile - returns the registered handler for a known name")
	void getFile_knownName_returnsHandler() {
		FileHandler fh = new FileHandler(plugin, "found", "yml");
		mgr.addFile(fh, false);

		assertSame(fh, mgr.getFile("found"));
	}

	@Test
	@DisplayName("getFile - returns null for an unknown name")
	void getFile_unknownName_returnsNull() {
		assertNull(mgr.getFile("does_not_exist"));
	}

	// ─────────────────────────────────────────── filesLoaded ──

	@Test
	@DisplayName("filesLoaded - returns true when all registered files are loaded")
	void filesLoaded_allLoaded_returnsTrue() throws IOException {
		FileHandler fh = new FileHandler(plugin, "all_loaded", "yml");
		fh.create(false);                   // loaded = true
		mgr.addFile(fh, false);

		assertTrue(mgr.filesLoaded());
	}

	@Test
	@DisplayName("filesLoaded - returns false when at least one file is not loaded")
	void filesLoaded_oneNotLoaded_returnsFalse() throws IOException {
		FileHandler loaded   = new FileHandler(plugin, "yes_loaded", "yml");
		FileHandler unloaded = new FileHandler(plugin, "not_loaded", "db");  // .db never sets loaded=true
		loaded.create(false);

		mgr.addFile(loaded, false);
		mgr.addFile(unloaded, false);

		assertFalse(mgr.filesLoaded(), "filesLoaded() must return false when at least one file is unloaded");
	}

	@Test
	@DisplayName("filesLoaded - returns true when the registry is empty")
	void filesLoaded_emptyRegistry_returnsTrue() {
		assertTrue(mgr.filesLoaded(), "An empty manager has vacuously all files loaded");
	}

	// ────────────────────────────────────────── checkFileLoaded ──

	@Test
	@DisplayName("checkFileLoaded - does not throw for a loaded YAML file")
	void checkFileLoaded_loadedFile_doesNotThrow() throws IOException {
		FileHandler fh = new FileHandler(plugin, "check_ok", "yml");
		fh.create(false);
		mgr.addFile(fh, false);

		assertDoesNotThrow(() -> mgr.checkFileLoaded("check_ok"));
	}

	@Test
	@DisplayName("checkFileLoaded - strips the extension before lookup")
	void checkFileLoaded_withExtension_stripsExtension() throws IOException {
		FileHandler fh = new FileHandler(plugin, "strip_ext", "yml");
		fh.create(false);
		mgr.addFile(fh, false);

		assertDoesNotThrow(() -> mgr.checkFileLoaded("strip_ext.yml"), "Extension should be stripped before lookup");
	}

	@Test
	@DisplayName("checkFileLoaded - throws FileNotFoundException for an unknown file name")
	void checkFileLoaded_unknownFile_throwsFileNotFoundException() {
		assertThrows(FileNotFoundException.class, () -> mgr.checkFileLoaded("ghost_file"));
	}

	@Test
	@DisplayName("checkFileLoaded - throws IOException for a registered but unloaded file")
	void checkFileLoaded_unloadedFile_throwsIOException() {
		FileHandler fh = new FileHandler(plugin, "unloaded_chk", "db");
		// db files are never loaded; do NOT call create()
		mgr.addFile(fh, false);

		assertThrows(IOException.class, () -> mgr.checkFileLoaded("unloaded_chk"));
	}

	// ──────────────────────────────────────────── clear ──

	@Test
	@DisplayName("clear - removes all registered file handlers")
	void clear_removesAllFiles() {
		mgr.addFile(new FileHandler(plugin, "a", "yml"), false);
		mgr.addFile(new FileHandler(plugin, "b", "yml"), false);
		mgr.addFile(new FileHandler(plugin, "c", "yml"), false);

		mgr.clear();

		assertEquals(0, mgr.getFiles().size());
		assertFalse(mgr.contains("a"));
	}

	// ──────────────────────────────────────── getFiles ──

	@Test
	@DisplayName("getFiles - returns an unmodifiable view of the internal set")
	void getFiles_returnsUnmodifiableView() {
		mgr.addFile(new FileHandler(plugin, "ro", "yml"), false);
		Set<FileHandler> files = mgr.getFiles();

		assertThrows(UnsupportedOperationException.class, () -> files.add(new FileHandler(plugin, "inject", "yml")));
	}

	@Test
	@DisplayName("getFiles - contains exactly the handlers that were added")
	void getFiles_exactlyAddedHandlers() {
		FileHandler a = new FileHandler(plugin, "fa", "yml");
		FileHandler b = new FileHandler(plugin, "fb", "yml");
		mgr.addFile(a, false);
		mgr.addFile(b, false);

		Set<FileHandler> files = mgr.getFiles();
		assertEquals(2, files.size());
		assertTrue(files.contains(a));
		assertTrue(files.contains(b));
	}

	// ──────────────────────────────────────── reloadFiles ──

	@Test
	@DisplayName("reloadFiles - does not throw even when a file does not exist on disk")
	void reloadFiles_filesMissing_doesNotThrow() throws IOException {
		FileHandler fh = new FileHandler(plugin, "reload_absent", "yml");
		fh.create(false);   // create it so initial add works
		mgr.addFile(fh, false);
		fh.delete();        // now remove it from disk

		assertDoesNotThrow(() -> mgr.reloadFiles(), "reloadFiles() must swallow errors and not propagate them");
	}

	@Test
	@DisplayName("reloadFiles - updates in-memory configuration from disk changes")
	void reloadFiles_picksUpDiskChanges() throws IOException {
		FileHandler fh = new FileHandler(plugin, "rld_disk", "yml");
		fh.create(false);
		mgr.addFile(fh, false);

		// Write data and save
		fh.getFileConfiguration().set("counter", 1);
		fh.save();

		// Simulate an external edit via a fresh handler
		FileHandler external = new FileHandler(plugin, "rld_disk", "yml");
		external.create(false);
		external.getFileConfiguration().set("counter", 77);
		external.save();

		// reloadFiles() should pick up the change
		mgr.reloadFiles();
		assertEquals(77, fh.getFileConfiguration().getInt("counter"),
		             "reloadFiles() must reflect the externally changed value");
	}
}
