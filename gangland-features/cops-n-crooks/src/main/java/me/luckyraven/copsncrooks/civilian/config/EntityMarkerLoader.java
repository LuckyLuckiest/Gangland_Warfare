package me.luckyraven.copsncrooks.civilian.config;

import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.item.ItemParser;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileLoader;
import me.luckyraven.persistence.FileManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

@Getter
@CustomLog
public class EntityMarkerLoader extends FileLoader<EntityMarkerConfig> {

	@Getter(AccessLevel.NONE)
	private final @Nullable ItemParser       itemParser;
	@Getter(AccessLevel.NONE)
	private final           CivilianSettings civilianSettings;

	private EntityMarkerConfig loadedConfig;

	public EntityMarkerLoader(JavaPlugin plugin, @Nullable ItemParser itemParser,
	                          CivilianSettings civilianSettings) {
		super(plugin);
		this.itemParser       = itemParser;
		this.civilianSettings = civilianSettings;
	}

	@Override
	public void clear() {
		loadedConfig = null;
	}

	@Override
	protected void loadData(Consumer<EntityMarkerConfig> consumer, FileManager fileManager) {
		FileConfiguration yaml;

		try {
			String fileName = "entity_marker";
			fileManager.checkFileLoaded(fileName);
			FileHandler handler = Objects.requireNonNull(fileManager.getFile(fileName));
			yaml = handler.getFileConfiguration();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		boolean aiEnabled  = civilianSettings.isCivilianAiEnabled();
		int     aiTickRate = civilianSettings.getCivilianAiTickRate();

		YamlEntityMarkerConfigProvider provider = new YamlEntityMarkerConfigProvider(yaml, aiEnabled, aiTickRate,
		                                                                             itemParser);
		loadedConfig = provider.getConfig();

		log.info("Loaded entity marker config with {} types and {} groups",
		         loadedConfig.types().size(), loadedConfig.groups().size());

		if (consumer != null) {
			consumer.accept(loadedConfig);
		}
	}
}
