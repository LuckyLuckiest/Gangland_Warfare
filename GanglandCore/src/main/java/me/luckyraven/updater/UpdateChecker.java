package me.luckyraven.updater;

import me.luckyraven.Gangland;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

	private final JavaPlugin plugin;
	private final int        resourceId;

	public UpdateChecker(JavaPlugin plugin, int resourceId) {
		this.plugin     = plugin;
		this.resourceId = resourceId;
	}

	public String getLatestVersion() {
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(
					"https://api.spigotmc.org/legacy/update.php?resource=" + resourceId).openConnection();

			connection.setRequestMethod("GET");
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);

			BufferedReader reader        = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String         latestVersion = reader.readLine();

			reader.close();

			return latestVersion;
		} catch (Exception exception) {
			Gangland.getLog4jLogger().error("Unable to check for the latest version.", exception);
		}

		return plugin.getDescription().getVersion();
	}

	public void downloadLatestVersion() {

	}

}
