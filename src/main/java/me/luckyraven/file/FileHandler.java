package me.luckyraven.file;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.util.ChatUtil;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

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
			ChatUtil.consoleColor(String.format("&cCould not&r save %s to %s: %s", name, file, exception.getMessage()));
		}
	}

	public void createNewFile() {
		File oldFile = new File(plugin.getDataFolder(), directory + "-old" + fileType);
		// TODO change renameTo to move
		if (oldFile.exists()) {
			File aOldFile;
			int  i = 0;
			do aOldFile = new File(plugin.getDataFolder(), directory + "-old (" + ++i + ")" + fileType);
			while (aOldFile.exists());
			file.renameTo(aOldFile);
		} else file.renameTo(oldFile);
		ChatUtil.consoleColor(
				String.format("%s%s &cis an old build or corrupted&r, creating a new one.", name, fileType));
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
