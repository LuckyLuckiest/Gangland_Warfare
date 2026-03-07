package me.luckyraven.copsncrooks.police.config;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import me.luckyraven.exception.PluginException;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileLoader;
import me.luckyraven.persistence.FileManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

@Log4j2
public class CopLoader extends FileLoader<CopConfig> {

	@Getter
	private CopConfig loadedConfig;

	@Getter
	private CopConfigProvider loadedProvider;

	public CopLoader(JavaPlugin plugin) {
		super(plugin);
	}

	@Override
	public void clear() {
		loadedConfig   = null;
		loadedProvider = null;
	}

	@Override
	protected void loadData(Consumer<CopConfig> consumer, FileManager fileManager) {
		FileConfiguration copsConfig;

		try {
			String copsFileName = "cops";
			fileManager.checkFileLoaded(copsFileName);
			FileHandler copsHandler = Objects.requireNonNull(fileManager.getFile(copsFileName));
			copsConfig = copsHandler.getFileConfiguration();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		loadedProvider = new YamlCopConfigProvider(copsConfig);
		loadedConfig   = CopConfig.fromProvider(loadedProvider);

		log.info("Loaded cop config with {} tiers and {} spawn locations", loadedConfig.getTiers().size(),
				 loadedConfig.getSpawnLocations().size());

		if (consumer != null) {
			consumer.accept(loadedConfig);
		}
	}

}
