package me.luckyraven.persistence;

import lombok.extern.log4j.Log4j2;
import me.luckyraven.util.UnhandledError;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Log4j2
public class FileManager {

	private final JavaPlugin       plugin;
	private final Set<FileHandler> files;

	public FileManager(JavaPlugin plugin) {
		this.files  = new HashSet<>();
		this.plugin = plugin;
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
	 * Reloads all the files are stored
	 */
	public void reloadFiles() {
		for (FileHandler file : files) {
			try {
				file.reloadData();
			} catch (IOException exception) {
				log.warn("{} {}.{}: {}", UnhandledError.FILE_LOADER_ERROR, file.getName(), file.getFileType(),
						 exception.getMessage());
			}
		}
	}

	/**
	 * Loads all the files that are stored in the plugin's resource folder without storing them in the files dataset
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

}
