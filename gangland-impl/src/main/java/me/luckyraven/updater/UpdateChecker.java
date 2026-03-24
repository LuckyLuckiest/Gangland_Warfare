package me.luckyraven.updater;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

@CustomLog
public class UpdateChecker {

	private final JavaPlugin     plugin;
	private final int            resourceId;
	private final RepeatingTimer repeatingTimer;
	private final AtomicBoolean  checked;
	@Getter
	private final String         checkPermission;

	public UpdateChecker(JavaPlugin plugin, String permissionPrefix, int resourceId, long interval) {
		this.plugin     = plugin;
		this.resourceId = resourceId;

		this.checked         = new AtomicBoolean();
		this.repeatingTimer  = new RepeatingTimer(plugin, interval * 20L, timer -> task());
		this.checkPermission = String.format("%s.update.check", permissionPrefix);
	}

	public String getLatestVersion() {
		String currentVersion = plugin.getDescription().getVersion();

		if (!isResourceIdSet()) return currentVersion;

		try {
			// create the url link
			URL url = new URI("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId).toURL();
			// establish the connection
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();

			// use GET request
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);

			// read the first line
			BufferedReader reader        = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String         latestVersion = reader.readLine();

			reader.close();

			// return the version specified from the first line
			return latestVersion;
		} catch (Exception exception) {
			log.error("Unable to check for the latest version.", exception);
		}

		return currentVersion;
	}

	public void downloadLatestVersion() {
		if (!isResourceIdSet()) return;

		try {
			String latestVersion = getLatestVersion();

			// get the url to download the resource from the Spiget API
			URL url = new URI("https://api.spiget.org/v2/resources/" + resourceId + "/download").toURL();
			// establish the connection by having a new channel
			ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());

			// create the release folder inside the plugin's data folder
			File releaseFolder = new File(plugin.getDataFolder(), "release");
			if (!releaseFolder.exists()) {
				boolean mkdir = releaseFolder.mkdirs();

				if (!mkdir) {
					log.error("Failed to create release folder at {}", releaseFolder.getAbsolutePath());
					return;
				}
			}

			// specify the output path for the JAR file
			File   outputFile = new File(releaseFolder, plugin.getName() + "-" + latestVersion + ".jar");
			String outputPath = outputFile.getAbsolutePath();

			// skip download if this version was already downloaded
			if (outputFile.exists()) {
				log.info("Version {} is already downloaded at {}", latestVersion, outputPath);
				return;
			}

			// create a file output stream to write the downloaded file
			FileOutputStream fileOutputStream = new FileOutputStream(outputPath);

			fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

			fileOutputStream.close();
			readableByteChannel.close();

			log.info("Downloaded version {} to {}", latestVersion, outputPath);
		} catch (FileNotFoundException exception) {
			log.error("Unable to find the new file.", exception);
		} catch (Exception exception) {
			log.error("Unable to download the new file.", exception);
		}
	}

	public void start() {
		if (this.repeatingTimer == null) return;

		log.info("Checking for updates");
		task();
		this.repeatingTimer.start(true);
	}

	public boolean updateAvailable() {
		return !getLatestVersion().equals(plugin.getDescription().getVersion());
	}

	public String getUpdateMessage() {
		if (!updateAvailable()) return "The plugin is up to date.";

		String newVersion     = getLatestVersion();
		String currentVersion = plugin.getDescription().getVersion();

		return String.format("The current version is %s, please update to the newest version available: %s",
							 currentVersion, newVersion);
	}

	private void task() {
		String updateMessage = getUpdateMessage();

		if (updateAvailable()) {
			ChatUtil.sendToOperators(checkPermission, updateMessage, log, true);
			if (Settings.isUpdaterAutoUpdate()) {
				downloadLatestVersion();
			}
			return;
		}

		if (checked.get()) return;

		ChatUtil.sendToOperators(checkPermission, updateMessage);
		checked.set(true);
	}

	private boolean isResourceIdSet() {
		return resourceId > -1;
	}

}
