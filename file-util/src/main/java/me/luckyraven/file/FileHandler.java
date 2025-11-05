package me.luckyraven.file;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.util.UnhandledError;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public class FileHandler {

	private static final Logger logger = LogManager.getLogger(FileHandler.class.getSimpleName());

	private final JavaPlugin plugin;
	private final String     fileType;
	@Getter
	private final String     name;

	private File   file;
	private String directory;

	@Getter
	private FileConfiguration fileConfiguration;
	@Getter
	private boolean           loaded;

	@Getter
	@Setter
	private String configVersion;

	public FileHandler(JavaPlugin plugin, File file) throws IOException {
		this(plugin, file.getName().substring(0, file.getName().lastIndexOf(".")),
			 file.getParentFile().getPath().substring(file.getParentFile().getPath().lastIndexOf("\\") + 1),
			 file.getName().substring(file.getName().lastIndexOf(".")));
		this.file = file;

		registerYamlFile();
	}

	public FileHandler(JavaPlugin plugin, String name, String fileType) {
		this(plugin, name, "", fileType);
	}

	public FileHandler(JavaPlugin plugin, String name, String directory, String fileType) {
		this.plugin        = plugin;
		this.name          = name;
		this.fileType      = fileType.startsWith(".") ? fileType : "." + fileType;
		this.directory     = directory.isEmpty() ? name : directory + File.separator + name;
		this.loaded        = false;
		this.configVersion = plugin.getDescription().getVersion();
	}

	public void create(boolean inJar) throws IOException {
		createNewFile(inJar);
		registerYamlFile();
	}

	public void delete() throws IOException {
		if (file != null && file.exists() && !file.delete()) throw new IOException("File not deleted");
	}

	public void save() {
		if (fileConfiguration != null && file != null) try {
			fileConfiguration.save(file);
		} catch (IOException exception) {
			logger.warn(String.format(UnhandledError.FILE_SAVE_ERROR + " %s to %s: %s", name, file,
									  exception.getMessage()));
		}
	}

	public void createNewFile() throws IOException {
		if (file == null) throw new IOException("File is not initialized");
		File oldFile = new File(plugin.getDataFolder(), directory + "-old" + fileType);

		if (oldFile.exists()) {
			int i = 0;
			do oldFile = new File(plugin.getDataFolder(), directory + "-old (" + ++i + ")" + fileType);
			while (oldFile.exists());
		}

		try {
			Files.move(file.toPath(), oldFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException exception) {
			String message = String.format(UnhandledError.FILE_EDIT_ERROR + " %s to %s: %s", oldFile.getName(),
										   file.getName(), exception.getMessage());
			logger.warn(message);
			return;
		}

		// create a new file since the previous one was moved
		createNewFile(true);
		logger.info(String.format("%s%s is an old build or corrupted, creating a new one", name, fileType));

		try (FileInputStream inputStream = new FileInputStream(file);
			 InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
			fileConfiguration.load(reader);
			loaded = true;
		} catch (IOException | InvalidConfigurationException ignored) {
			loaded = false;
		}
	}

	public void reloadData() throws IOException {
		// reload to apply changes
		reload();

		// need to check configuration was changed
		if (validateConfigVersion()) reload();
	}

	public String getDirectory() {
		return file.getAbsolutePath();
	}

	public void setDirectory(String directory) {
		this.directory = directory + File.separator + name;
	}

	public String getFileType() {
		return fileType.substring(1);
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) return true;
		if (object == null || getClass() != object.getClass()) return false;

		FileHandler that = (FileHandler) object;

		if (!Objects.equals(name, that.name)) return false;
		if (!Objects.equals(fileType, that.fileType)) return false;
		return Objects.equals(directory, that.directory);
	}

	private void createNewFile(boolean inJar) throws IOException {
		String resourceFile = directory + fileType;
		if (file == null) file = new File(plugin.getDataFolder().getAbsolutePath(), resourceFile);

		if (file.exists()) return;

		file.getParentFile().mkdirs();
		if (plugin.getResource(resourceFile.replace(File.separator, "/")) != null && inJar) plugin.saveResource(
				resourceFile, false);
		else {
			int dir = file.getName().lastIndexOf(File.separator);

			if (dir != -1) {
				File folder = new File(plugin.getDataFolder(), file.getName().substring(0, dir));

				// create a new folder
				if (!folder.exists()) folder.mkdir();
			}
			file.createNewFile();
		}
	}

	private void registerYamlFile() throws IOException {
		if (!fileType.equals(".yml")) return;
		fileConfiguration = new YamlConfiguration();
		try (FileInputStream inputStream = new FileInputStream(file);
			 InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
			fileConfiguration.load(reader);
			loaded = true;

			validateConfigVersion();
		} catch (InvalidConfigurationException exception) {
			loaded = false;
		}
	}

	private boolean validateConfigVersion() throws IOException {
		String configVersion = fileConfiguration.getString("Config_Version");
		if (configVersion != null)
			if (!Objects.equals(fileConfiguration.getString("Config_Version"), this.configVersion)) {
				createNewFile();
				return true;
			}

		return false;
	}

	private void reload() {
		// throwing fileConfiguration
		if (fileConfiguration != null) fileConfiguration = null;

		// applying the value again
		fileConfiguration = YamlConfiguration.loadConfiguration(file);
	}

}
