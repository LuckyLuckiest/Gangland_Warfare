package org.luckyraven.gangland.copsncrooks.npc.civilian.config;

import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.gangland.item.ItemParser;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileLoader;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.FileHandlerReader;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

@Getter
@CustomLog
public class CiviliansLoader extends FileLoader<CiviliansConfig> {

	@Getter(AccessLevel.NONE)
	private final @Nullable ItemParser       itemParser;
	@Getter(AccessLevel.NONE)
	private final           CivilianSettings civilianSettings;

	private CiviliansConfig loadedConfig;

	public CiviliansLoader(JavaPlugin plugin, @Nullable ItemParser itemParser, CivilianSettings civilianSettings,
	                       boolean disable, @Nullable Consumer<CiviliansConfig> consumer, FileManager fileManager) {
		super(plugin, disable, consumer, fileManager);
		this.itemParser       = itemParser;
		this.civilianSettings = civilianSettings;
	}

	@Override
	public void clear() {
		loadedConfig = null;
	}

	@Override
	protected FileHandler resolvePrimaryHandler(FileManager fileManager) {
		return fileManager.getFile("civilians");
	}

	@Override
	protected void loadData(Consumer<CiviliansConfig> consumer, FileManager fileManager) {
		FileHandler handler;

		try {
			String fileName = "civilians";
			fileManager.checkFileLoaded(fileName);
			handler = Objects.requireNonNull(fileManager.getFile(fileName));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		boolean aiEnabled  = civilianSettings.isCivilianAiEnabled();
		int     aiTickRate = civilianSettings.getCivilianAiTickRate();

		ConfigReport report = new ConfigReport();
		NodeReader   reader = FileHandlerReader.read(handler, report);

		YamlCiviliansConfigProvider provider = new YamlCiviliansConfigProvider(reader, report, aiEnabled, aiTickRate,
		                                                                       itemParser);
		loadedConfig = provider.getConfig();

		if (!report.isEmpty()) report.log(log);

		log.debug("Loaded civilians config with {} types and {} groups", loadedConfig.types().size(),
		          loadedConfig.groups().size());

		if (consumer != null) {
			consumer.accept(loadedConfig);
		}
	}
}
