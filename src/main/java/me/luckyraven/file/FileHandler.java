package me.luckyraven.file;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.util.UnhandledError;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class FileHandler {

	private @Getter FileConfiguration fileConfiguration;

	private File file;

	private @Getter
	final String name;

	private String directory;

	private final String fileType;

	private @Getter
	@Setter int configVersion;

	private @Getter boolean loaded;

	private final JavaPlugin plugin;

	public FileHandler(JavaPlugin plugin, String name, String fileType) {
		this(plugin, name, "", fileType);
		this.directory = this.directory.substring(1);
	}

	public FileHandler(JavaPlugin plugin, String name, String directory, String fileType) {
		this.plugin = plugin;
		this.name = name;
		this.fileType = fileType;
		this.directory = directory + "\\" + name;
		this.loaded = false;
		this.configVersion = 0;
	}

	public void create() throws IOException {
		file = new File(plugin.getDataFolder(), directory + fileType);

		if (!file.exists()) {
			file.getParentFile().mkdirs();
			plugin.saveResource(directory + fileType, false);
		}

		if (fileType.equals(".yml")) {
			fileConfiguration = new YamlConfiguration();
			try {
				fileConfiguration.load(file);
				loaded = true;
				if (fileConfiguration.getInt("Config_Version") != configVersion) createNewFile();
			} catch (InvalidConfigurationException exception) {
				loaded = false;
			}
		}
	}

	public void save() {
		if (fileConfiguration != null && file != null) try {
			fileConfiguration.save(file);
		} catch (IOException exception) {
			plugin.getLogger().warning(String.format(UnhandledError.FILE_SAVE_ERROR + " %s to %s: %s", name, file,
			                                         exception.getMessage()));
		}
	}

	public void createNewFile() {
		File oldFile = new File(plugin.getDataFolder(), directory + "-old" + fileType);

		if (oldFile.exists()) {
			File aOldFile;
			int  i = 0;
			do aOldFile = new File(plugin.getDataFolder(), directory + "-old (" + ++i + ")" + fileType);
			while (aOldFile.exists());
			try {
				Files.move(file.toPath(), aOldFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException exception) {
				plugin.getLogger().warning(
						String.format(UnhandledError.FILE_EDIT_ERROR.getMessage() + " %s to %s: %s", aOldFile.getName(),
						              file.getName(), exception.getMessage()));
			}
		} else try {
			Files.move(file.toPath(), oldFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException exception) {
			plugin.getLogger().warning(
					String.format(UnhandledError.FILE_EDIT_ERROR.getMessage() + " %s to %s: %s", oldFile.getName(),
					              file.getName(), exception.getMessage()));
		}

		plugin.getLogger().info(String.format("%s%s is an old build or corrupted, creating a new one", name, fileType));

		try {
			plugin.saveResource(directory + fileType, false);
			fileConfiguration.load(file);
			loaded = true;
		} catch (IOException | InvalidConfigurationException ignored) {
			loaded = false;
		}
	}

	public String getDirectory() {
		return file.getAbsolutePath();
	}

	public void setDirectory(String directory) {
		this.directory = directory + "\\" + name;
	}

	public String getFileType() {
		return fileType.substring(1);
	}

}
