package me.luckyraven.copsncrooks.police.config;

import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileLoader;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.util.item.ItemParser;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

@Getter
@CustomLog
public class CopLoader extends FileLoader<CopConfig> {

	@Getter(AccessLevel.NONE)
	private final ItemParser itemParser;

	@Getter(AccessLevel.NONE)
	private final CopSettings copSettings;

	private CopConfig         loadedConfig;
	private CopConfigProvider loadedProvider;

	public CopLoader(JavaPlugin plugin, @Nullable ItemParser itemParser,
					 @Nullable CopSettings copSettings) {
		super(plugin);
		this.itemParser  = itemParser;
		this.copSettings = copSettings;
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

		loadedProvider = new YamlCopConfigProvider(copsConfig, copSettings, itemParser);
		loadedConfig   = CopConfig.fromProvider(loadedProvider);

		log.info("Loaded cop config with {} tiers", loadedConfig.getTiers().size());

		if (consumer != null) {
			consumer.accept(loadedConfig);
		}
	}
}
