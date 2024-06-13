package me.luckyraven.updater;

import me.luckyraven.Gangland;
import org.bukkit.Bukkit;
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

	public void getLatestVersion() {
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(
					"https://api.spigotmc.org/legacy/update.php?resource=" + resourceId).openConnection();

			connection.setRequestMethod("GET");
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);

			BufferedReader reader        = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String         latestVersion = reader.readLine();

			reader.close();

			String currentVersion = plugin.getDescription().getVersion();

			if (latestVersion != null && !latestVersion.equals(currentVersion)) {
				Gangland.getLog4jLogger()
						.info("The current version is {}, please update to the newest version available: {}",
							  currentVersion, latestVersion);
			} else {
				Gangland.getLog4jLogger().info("The plugin is up to date.");
			}
		} catch (Exception exception) {
			Gangland.getLog4jLogger().error("checking for latest version error.", exception);
		}
	}

	public void downloadLatestVersion() {

	}

}
