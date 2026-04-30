package org.luckyraven.gangland.copsncrooks.npc.police.config;

import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.exception.PluginException;
import org.luckyraven.gangland.item.ItemParser;
import org.luckyraven.gangland.persistence.FileHandler;
import org.luckyraven.gangland.persistence.FileLoader;
import org.luckyraven.gangland.persistence.FileManager;
import org.luckyraven.gangland.persistence.config.ConfigReport;
import org.luckyraven.gangland.persistence.config.FileHandlerReader;
import org.luckyraven.gangland.persistence.config.NodeReader;

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

	public CopLoader(JavaPlugin plugin, @Nullable ItemParser itemParser, @Nullable CopSettings copSettings,
	                 boolean disable, @Nullable Consumer<CopConfig> consumer, FileManager fileManager) {
		super(plugin, disable, consumer, fileManager);
		this.itemParser  = itemParser;
		this.copSettings = copSettings;
	}

	@Override
	public void clear() {
		loadedConfig   = null;
		loadedProvider = null;
	}

	@Override
	protected FileHandler resolvePrimaryHandler(FileManager fileManager) {
		return fileManager.getFile("cops");
	}

	@Override
	protected void loadData(Consumer<CopConfig> consumer, FileManager fileManager) {
		FileHandler copsHandler;

		try {
			String copsFileName = "cops";
			fileManager.checkFileLoaded(copsFileName);
			copsHandler = Objects.requireNonNull(fileManager.getFile(copsFileName));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		ConfigReport report = new ConfigReport();
		NodeReader   reader = FileHandlerReader.read(copsHandler, report);

		loadedProvider = new YamlCopConfigProvider(reader, report, copSettings, itemParser);
		loadedConfig   = CopConfig.fromProvider(loadedProvider);

		if (!report.isEmpty()) report.log(log);

		log.debug("Loaded cop config with {} tiers", loadedConfig.getTiers().size());

		if (consumer != null) {
			consumer.accept(loadedConfig);
		}
	}
}
