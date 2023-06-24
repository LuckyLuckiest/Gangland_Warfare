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
import java.util.Objects;

public class FileHandler {

	private @Getter FileConfiguration fileConfiguration;

	private File file;

	private @Getter
	final String name;

	private String directory;

	private final String fileType;

	private @Getter
	@Setter String configVersion;

	private @Getter boolean loaded;

	private final JavaPlugin plugin;

	public FileHandler(JavaPlugin plugin, String name, String fileType) {
		this(plugin, name, "", fileType);
	}

	public FileHandler(JavaPlugin plugin, String name, String directory, String fileType) {
		this.plugin = plugin;
		this.name = name;
		this.fileType = fileType.startsWith(".") ? fileType : "." + fileType;
		this.directory = directory.isEmpty() ? name : directory + "\\" + name;
		this.loaded = false;
		this.configVersion = plugin.getDescription().getVersion();
	}

	public void create(boolean inJar) throws IOException {
		file = new File(plugin.getDataFolder(), directory + fileType);

		if (!file.exists()) {
			file.getParentFile().mkdirs();
			if (plugin.getResource(directory + fileType) != null && inJar) plugin.saveResource(directory + fileType,
			                                                                                   false);
			else {
				int dir = file.getName().lastIndexOf("\\");

				if (dir != -1) {
					File folder = new File(plugin.getDataFolder(), file.getName().substring(0, dir));

					// create a new folder
					if (!folder.exists()) folder.mkdir();
				}
				file.createNewFile();
			}
		}

		if (fileType.equals(".yml")) {
			fileConfiguration = new YamlConfiguration();
			try {
				fileConfiguration.load(file);
				loaded = true;
				if (!Objects.equals(fileConfiguration.getString("Config_Version"), configVersion)) createNewFile();
			} catch (InvalidConfigurationException exception) {
				loaded = false;
			}
		}
	}

	public void delete() throws IOException {
		if (file != null && file.exists() && !file.delete()) throw new IOException("File not deleted");
	}

	public void save() {
		if (fileConfiguration != null && file != null) try {
			fileConfiguration.save(file);
		} catch (IOException exception) {
			plugin.getLogger().warning(
					String.format(UnhandledError.FILE_SAVE_ERROR.getMessage() + " %s to %s: %s", name, file,
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
