package me.luckyraven.data;

import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * The type Data loader.
 */
public abstract class DataLoader {

	private boolean isLoaded = false;

	/**
	 * Loads data from the plugin.
	 */
	protected abstract void loadData();

	/**
	 * Used to communicate with the program if all the data is loaded inside the plugin.
	 *
	 * @return boolean value of loaded data variable.
	 */
	public boolean isDataLoaded() {
		return isLoaded;
	}

	/**
	 * This method loads the data of the class if there was a cause of problem inside the data, it stops the process
	 * and prints the error.
	 *
	 * @param disable disables the plugin upon finding an exception.
	 */
	public void load(JavaPlugin plugin, boolean disable) {
		try {
			loadData();
			isLoaded = true;
		} catch (Exception exception) {
			plugin.getLogger().log(Level.SEVERE,
			                       "The plugin data has ran into a problem, please check the logs and report them to the developer.",
			                       exception);
			if (disable) Bukkit.getPluginManager().disablePlugin(plugin);
		}
	}

	/**
	 * This method tries to load the data again (until it is loaded) if there was a cause of problem inside the data, it
	 * stops the process and prints the error.
	 *
	 * @param disable disables the plugin upon finding an exception.
	 */
	public void tryAgain(JavaPlugin plugin, boolean disable) {
		int maxAttempts = 5, initialValue = 5, counter = 0;

		load(plugin, disable);

		if (isLoaded) return;

		// TODO make this instruction run every 5 seconds, and increment accordingly to X times until it fails
		++counter;

		CountdownTimer timer = new CountdownTimer(plugin, initialValue, time -> load(plugin, disable));

		timer.startAsync();
	}

}
