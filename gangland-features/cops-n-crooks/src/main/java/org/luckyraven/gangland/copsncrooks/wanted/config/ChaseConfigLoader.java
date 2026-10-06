package org.luckyraven.gangland.copsncrooks.wanted.config;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileLoader;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.FileHandlerReader;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Loads {@code copsncrooks/wanted.yml} into a {@link ChaseConfig}. Consumers call {@link #get()} per use, so a reload is seen
 * live; before the first load, or when the file is missing, it is {@link ChaseConfig#DEFAULT}.
 *
 * @since 0.15.0
 */
@CustomLog
public class ChaseConfigLoader extends FileLoader<ChaseConfig> {

	private static final String FILE_NAME = "wanted";

	private volatile ChaseConfig loaded = ChaseConfig.DEFAULT;

	public ChaseConfigLoader(JavaPlugin plugin, FileManager fileManager) {
		super(plugin, false, null, fileManager);
	}

	/** The current config, never null. */
	public ChaseConfig get() {
		return loaded;
	}

	@Override
	public void clear() {
		loaded = ChaseConfig.DEFAULT;
	}

	@Override
	protected FileHandler resolvePrimaryHandler(FileManager fileManager) {
		return fileManager.getFile(FILE_NAME);
	}

	@Override
	protected void loadData(Consumer<ChaseConfig> consumer, FileManager fileManager) {
		FileHandler handler;

		try {
			fileManager.checkFileLoaded(FILE_NAME);
			handler = Objects.requireNonNull(fileManager.getFile(FILE_NAME));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		ConfigReport report  = new ConfigReport();
		NodeReader   reader  = FileHandlerReader.read(handler, report);
		MappingNode  section = reader.get("Wanted").asMapping().orNull();

		loaded = ChaseConfig.parse(section != null ? NodeReader.of(section, report) : null, report);

		if (!report.isEmpty()) report.log(log);

		if (consumer != null) {
			consumer.accept(loaded);
		}
	}
}
