package me.luckyraven.file;

import me.luckyraven.util.UnhandledError;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FileManager {

	private final List<FileHandler> files;
	private final JavaPlugin        plugin;

	public FileManager(JavaPlugin plugin) {
		this.files = new ArrayList<>();
		this.plugin = plugin;
	}

	public void addFile(FileHandler file, boolean create) {
		files.add(file);
		if (!create) return;
		try {
			file.create(true);
		} catch (IOException exception) {
			plugin.getLogger()
			      .warning(String.format(UnhandledError.FILE_CREATE_ERROR + " %s.%s: %s", file.getName(),
			                             file.getFileType(), exception.getMessage()));
		}
	}

	@Nullable
	public FileHandler getFile(String fileName) {
		for (FileHandler file : files) if (file.getName().equalsIgnoreCase(fileName)) return file;
		return null;
	}

	public boolean filesLoaded() {
		for (FileHandler file : files) if (!file.isLoaded()) return false;
		return true;
	}

	public void checkFileLoaded(String name) throws IOException {
		FileHandler file = getFile(name);
		if (file == null) throw new FileNotFoundException(String.format("%s does not exist!", name));
		if (!file.isLoaded()) throw new IOException(String.format("%s file is not loaded!", name));
	}

	public void reloadFiles() {
		for (FileHandler file : files)
			try {
				file.reloadData();
			} catch (IOException exception) {
				plugin.getLogger()
				      .warning(String.format(UnhandledError.FILE_LOADER_ERROR + " %s.%s: %s", file.getName(),
				                             file.getFileType(), exception.getMessage()));
			}
	}

	public YamlConfiguration loadFromResources(String resourceFile) throws IOException, InvalidConfigurationException {
		File        file = new File(plugin.getDataFolder().getAbsolutePath(), resourceFile);
		InputStream inputStream;

		// Checks for the file in system
		if (plugin.getDataFolder().exists() && file.exists()) inputStream = new FileInputStream(file);
			// Checks for the file in resources
		else inputStream = plugin.getResource(resourceFile.replace(File.separator, "/"));

		if (inputStream == null) {
			throw new FileNotFoundException(String.format("%s is not registered!", resourceFile.substring(
					resourceFile.lastIndexOf(File.separator) + 1)));
		}

		YamlConfiguration yamlConfiguration = new YamlConfiguration();
		yamlConfiguration.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

		return yamlConfiguration;
	}

	public List<FileHandler> getFiles() {
		return new ArrayList<>(files);
	}

}
