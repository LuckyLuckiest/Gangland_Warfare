package me.luckyraven.data;

import me.luckyraven.Gangland;
import org.bukkit.Bukkit;

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
	 * This method loads the data of the class, if there was a cause of problem inside the data it stops the process
	 * and prints the error.
	 *
	 * @param disable disables the plugin upon finding an exception.
	 */
	public void load(boolean disable) {
		try {
			loadData();
			isLoaded = true;
		} catch (Exception exception) {
			exception.printStackTrace();
			Gangland gangland = Gangland.getInstance();
			gangland.getLogger().log(Level.SEVERE,
			                         "The plugin data has ran into a problem, please check the logs and report them to the developer.");
			if (disable) Bukkit.getPluginManager().disablePlugin(gangland);
		}
	}

	/**
	 * This method tries to load the data again (until it is loaded), if there was a cause of problem inside the data it
	 * stops the process and prints the error.
	 *
	 * @param disable disables the plugin upon finding an exception.
	 */
	public void tryAgain(boolean disable) {
		// TODO make this instruction run every 5 seconds, and increment accordingly to X times until it fails
		while (!isLoaded) load(disable);
	}

}
