package me.luckyraven.persistence;

import lombok.CustomLog;
import me.luckyraven.core.UnhandledError;
import me.luckyraven.core.bean.BeanLifecycle;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@CustomLog
public class FileManager implements BeanLifecycle {

	private final JavaPlugin            plugin;
	private final Set<FileHandler>      files;
	private final List<FileInitializer> initializers;
	private       int                   nextInitializerIndex;

	public FileManager(JavaPlugin plugin) {
		this.files                = new HashSet<>();
		this.initializers         = new ArrayList<>();
		this.nextInitializerIndex = 0;
		this.plugin               = plugin;
	}

	/**
	 * Adds the FileHandler to the set of stored files locally and creates the file if required
	 *
	 * @param file the FileHandler that stores the necessary information of the file
	 * @param create creates the file in the plugin directory if required
	 */
	public void addFile(FileHandler file, boolean create) {
		if (files.contains(file)) return;

		files.add(file);
		if (!create) return;
		try {
			file.create(true);
		} catch (IOException exception) {
			log.warn("{} {}.{}: {}", UnhandledError.FILE_CREATE_ERROR, file.getName(), file.getFileType(),
			         exception.getMessage());
		}
	}

	/**
	 * Clears the dataset of the files stored
	 */
	public void clear() {
		files.clear();
	}

	/**
	 * Checks if the file is already stored in the dataset
	 *
	 * @param fileName used to check for the file
	 *
	 * @return boolean value if the file is there
	 */
	public boolean contains(String fileName) {
		for (FileHandler file : files) {
			if (file.getName().equalsIgnoreCase(fileName)) return true;
		}

		return false;
	}

	/**
	 * Gets the FileHandler from the stored dataset
	 *
	 * @param fileName looks for the file in the dataset
	 *
	 * @return the FileHandler if found, null otherwise
	 */
	@Nullable
	public FileHandler getFile(String fileName) {
		for (FileHandler file : files) {
			if (file.getName().equalsIgnoreCase(fileName)) return file;
		}

		return null;
	}

	/**
	 * Checks if the files are all loaded successfully
	 *
	 * @return boolean value if all files are loaded
	 */
	public boolean filesLoaded() {
		for (FileHandler file : files) {
			if (!file.isLoaded()) return false;
		}

		return true;
	}

	/**
	 * Checks for the specified file name if it was loaded. The file extension is ignored.
	 *
	 * @param name the file name that is being checked
	 *
	 * @throws IOException if the file is not loaded
	 */
	public void checkFileLoaded(String name) throws IOException {
		// remove the extension
		String cleanedName;

		if (name.contains(".")) cleanedName = name.substring(0, name.lastIndexOf('.'));
		else cleanedName = name;

		FileHandler file = getFile(cleanedName);

		if (file == null) throw new FileNotFoundException(String.format("%s does not exist!", cleanedName));
		if (!file.isLoaded()) throw new IOException(String.format("%s file is not loaded!", cleanedName));
	}

	/**
	 * Reloads every stored file from disk. If a file is missing or corrupted, it is regenerated from the bundled jar
	 * resource so subsequent {@link FileInitializer}s see fresh data instead of the stale in-memory state from before
	 * the reload.
	 */
	public void reloadFiles() {
		for (FileHandler file : files) {
			try {
				file.reloadData();
			} catch (IOException exception) {
				log.warn("{} {}.{}: {} - regenerating from jar", UnhandledError.FILE_LOADER_ERROR, file.getName(),
				         file.getFileType(), exception.getMessage());
				try {
					file.createNewFile();
				} catch (IOException recreate) {
					log.error("Could not regenerate {}.{} on reload: {}", file.getName(), file.getFileType(),
					          recreate.getMessage());
				}
			}
		}
	}

	/**
	 * Loads all the files that are stored in the plugin's resource folder without storing them in the file dataset
	 *
	 * @param resourceFile the path of a file in the resource folder
	 *
	 * @return YamlConfiguration of the file
	 *
	 * @throws IOException                   if the file is not found or not loaded properly
	 * @throws InvalidConfigurationException if the file is not loaded properly
	 */
	public YamlConfiguration loadFromResources(String resourceFile) throws IOException, InvalidConfigurationException {
		File        file = new File(plugin.getDataFolder().getAbsolutePath(), resourceFile);
		InputStream inputStream;

		// Checks for the file in the system
		if (plugin.getDataFolder().exists() && file.exists()) {
			inputStream = new FileInputStream(file);
		}
		// Checks for the file in resources
		else {
			inputStream = plugin.getResource(resourceFile.replace(File.separator, "/"));
		}

		if (inputStream == null) {
			throw new FileNotFoundException(String.format("%s is not registered!", resourceFile.substring(
					resourceFile.lastIndexOf(File.separator) + 1)));
		}

		YamlConfiguration yamlConfiguration = new YamlConfiguration();
		yamlConfiguration.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

		return yamlConfiguration;
	}

	/**
	 * Gets an unmodifiable set of all the files that are currently loaded by the manager
	 *
	 * @return unmodifiable set of files
	 */
	public Set<FileHandler> getFiles() {
		return Collections.unmodifiableSet(files);
	}

	// ── BeanLifecycle ─────────────────────────────────────────────────────────

	@Override
	public void onClear() {
		for (FileInitializer initializer : initializers) {
			initializer.clear();
		}
		nextInitializerIndex = 0;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (firstLoad) return;
		reloadFiles();
		initializeAll();
	}

	/**
	 * Registers a file-backed initializer so {@link #initializeAll()} can run it with automatic recovery (regenerate
	 * the underlying file from the bundled jar resource and retry once if the first attempt throws).
	 *
	 * @param initializer the initializer to register
	 */
	public void registerInitializer(FileInitializer initializer) {
		initializers.add(initializer);
	}

	/**
	 * Drops every registered initializer and resets the iteration cursor. Call this at the start of a reload so the
	 * registry doesn't leak references to the old addon instances that {@code addonsClear()} just discarded.
	 */
	public void clearInitializers() {
		initializers.clear();
		nextInitializerIndex = 0;
	}

	/**
	 * Runs every {@link FileInitializer} that has been registered since the last call, in registration order. Any
	 * failure is logged loudly, the underlying file is regenerated from the bundled jar resource via
	 * {@link FileHandler#createNewFile()}, and the initializer is retried once. A second failure is logged at error
	 * level and the loop continues to the next initializer rather than aborting plugin load.
	 * <p>
	 * Subsequent calls only process initializers registered after the previous call, so the orchestrator can be invoked
	 * in stages when later loaders depend on values populated by earlier ones.
	 */
	public void initializeAll() {
		while (nextInitializerIndex < initializers.size()) {
			runInitializer(initializers.get(nextInitializerIndex));
			nextInitializerIndex++;
		}
	}

	private void runInitializer(FileInitializer initializer) {
		FileHandler handler = initializer.getFileHandler();

		try {
			initializer.initialize();
		} catch (Exception first) {
			if (handler == null) {
				// Multi-file or folder loader - no single file to regenerate. Just log and move on.
				log.error("Failed to initialize {}: {}", initializer.getClass().getSimpleName(), first.getMessage(),
				          first);
				return;
			}

			log.warn("Failed to initialize {}.{}: {} - regenerating from jar and retrying", handler.getName(),
			         handler.getFileType(), first.getMessage());
			try {
				handler.createNewFile();
			} catch (IOException recreate) {
				log.error("Could not regenerate {}.{}: {}", handler.getName(), handler.getFileType(),
				          recreate.getMessage());
				return;
			}
			try {
				initializer.initialize();
			} catch (Exception second) {
				log.error("Initialization still failing for {}.{} after regeneration: {}", handler.getName(),
				          handler.getFileType(), second.getMessage(), second);
			}
		}
	}

}
