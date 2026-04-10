package me.luckyraven.file.configuration;

import me.luckyraven.exception.PluginException;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileInitializer;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.util.autowire.bean.BeanLifecycle;

import java.io.IOException;
import java.util.Objects;

/**
 * Bridges {@link MoneyAddon} to the {@link FileInitializer} recovery loop without dragging the
 * {@code plugin-persistence} dependency into the gangland-item module. {@code MoneyAddon} is intentionally
 * module-isolated and only knows how to consume a {@code ConfigurationSection}; this wrapper owns the {@code money.yml}
 * {@link FileHandler} and feeds the loaded config into the addon when {@link #initialize()} runs.
 */
public class MoneyAddonInitializer implements FileInitializer, BeanLifecycle {

	private final FileHandler fileHandler;
	private final MoneyAddon  moneyAddon;

	public MoneyAddonInitializer(FileManager fileManager, MoneyAddon moneyAddon) {
		this.moneyAddon = moneyAddon;

		try {
			String fileName = "money";

			fileManager.checkFileLoaded(fileName);

			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		moneyAddon.load(fileHandler.getFileConfiguration());
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (firstLoad) return;
		moneyAddon.setEnabled(Settings.isMoneyDropEnabled());
	}

	@Override
	public void clear() {
		moneyAddon.clear();
	}
}
