package me.luckyraven.persistence;

import lombok.extern.log4j.Log4j2;
import me.luckyraven.exception.PluginException;
import me.luckyraven.util.timer.SequenceTimer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

@Log4j2
public abstract class FileLoader<T> {

	private final JavaPlugin plugin;

	private boolean isLoaded = false;

	public FileLoader(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * Clears the data from the plugin.
	 */
	public abstract void clear();

	/**
	 * Loads data from the plugin.
	 */
	protected abstract void loadData(Consumer<T> consumer, FileManager fileManager);

	/**
	 * Used to communicate with the program if all the data is loaded inside the plugin.
	 *
	 * @return boolean value of loaded data variable.
	 */
	public boolean isDataLoaded() {
		return isLoaded;
	}

	/**
	 * This method loads the data of the class if there was a cause of problem inside the data, it stops the process and
	 * prints the error.
	 *
	 * @param disable disables the plugin upon finding an exception.
	 */
	public void load(boolean disable, Consumer<T> consumer, FileManager fileManager) throws PluginException {
		try {
			loadData(consumer, fileManager);
			isLoaded = true;
		} catch (Throwable throwable) {
			String message = String.format(
					"The plugin data has ran into a problem, please check the logs and report them to the developer." +
					"\nVersion: %s", plugin.getDescription().getVersion());

			log.error(message, throwable);

			if (disable) Bukkit.getPluginManager().disablePlugin(plugin);
			throw new PluginException(throwable);
		}
	}

	/**
	 * This method tries to load the data again (until it is loaded) if there was a cause of problem inside the data, it
	 * stops the process and prints the error.
	 *
	 * @param disable disables the plugin upon finding an exception.
	 */
	public void tryAgain(boolean disable, Consumer<T> consumer, FileManager fileManager) {
		int           maxAttempts = 5, initialValue = 5;
		SequenceTimer timer       = new SequenceTimer(plugin);

		for (int i = 1; i <= maxAttempts; i++) {
			timer.addIntervalTaskPair(initialValue * 20L, time -> {
				// if the process was successful, then stop the timer
				if (isLoaded) time.stop();

				load(disable, consumer, fileManager);
			});

			initialValue += i * initialValue;
		}

		timer.start(false);
	}

}
