package me.luckyraven.file;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
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
			file.create();
		} catch (IOException exception) {
			plugin.getLogger().warning(
					String.format("Unable to create file %s.%s, reason: %s", file.getName(), file.getFileType(),
					              exception.getMessage()));
		}
	}

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

	public YamlConfiguration loadFromResources(String resourceFile) throws IOException, InvalidConfigurationException {
		checkFileLoaded("settings");

		File        file = new File(plugin.getDataFolder().getAbsolutePath() + "\\" + resourceFile);
		InputStream inputStream;

		// Checks for the file in system
		if (plugin.getDataFolder().exists() && file.exists()) inputStream = new FileInputStream(file);
			// Checks for the file in resources
		else inputStream = plugin.getResource(resourceFile.replace("\\", "/"));

		if (inputStream == null) throw new FileNotFoundException(
				String.format("%s is not registered!", resourceFile.substring(resourceFile.lastIndexOf("\\") + 1)));

		YamlConfiguration yamlConfiguration = new YamlConfiguration();
		yamlConfiguration.load(new InputStreamReader(inputStream));

		return yamlConfiguration;
	}

	public List<FileHandler> getFiles() {
		return new ArrayList<>(files);
	}

}
