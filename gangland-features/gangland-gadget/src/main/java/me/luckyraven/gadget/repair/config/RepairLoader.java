package me.luckyraven.gadget.repair.config;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileLoader;
import me.luckyraven.persistence.FileManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

@Getter
@CustomLog
public class RepairLoader extends FileLoader<RepairConfig> {

	private RepairConfig         loadedConfig;
	private RepairConfigProvider loadedProvider;

	public RepairLoader(JavaPlugin plugin) {
		super(plugin);
	}

	@Override
	public void clear() {
		loadedConfig   = null;
		loadedProvider = null;
	}

	@Override
	protected FileHandler resolvePrimaryHandler(FileManager fileManager) {
		return fileManager.getFile("repair");
	}

	@Override
	protected void loadData(Consumer<RepairConfig> consumer, FileManager fileManager) {
		FileConfiguration repairConfig;

		try {
			String repairFileName = "repair";
			fileManager.checkFileLoaded(repairFileName);
			FileHandler repairHandler = Objects.requireNonNull(fileManager.getFile(repairFileName));
			repairConfig = repairHandler.getFileConfiguration();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		loadedProvider = new YamlRepairConfigProvider(repairConfig);
		loadedConfig   = RepairConfig.fromProvider(loadedProvider);

		log.info("Loaded repair config with {} material types", loadedConfig.getRepairMaterials().size());

		if (consumer != null) {
			consumer.accept(loadedConfig);
		}
	}
}
