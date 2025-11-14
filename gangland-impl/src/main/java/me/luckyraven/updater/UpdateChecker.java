package me.luckyraven.updater;

import lombok.Getter;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.timer.RepeatingTimer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class UpdateChecker {

	private static final Logger logger = LogManager.getLogger(UpdateChecker.class.getSimpleName());

	private final JavaPlugin     plugin;
	private final int            resourceId;
	private final RepeatingTimer repeatingTimer;
	private final AtomicBoolean  checked;
	@Getter
	private final String         checkPermission = "gangland.update.check";

	public UpdateChecker(JavaPlugin plugin, int resourceId, long interval) {
		this.plugin         = plugin;
		this.resourceId     = resourceId;
		this.checked        = new AtomicBoolean();
		this.repeatingTimer = new RepeatingTimer(plugin, interval, timer -> task());
	}

	public String getLatestVersion() {
		String currentVersion = plugin.getDescription().getVersion();

		if (!checkUpdate()) return currentVersion;

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
			logger.error("Unable to check for the latest version.", exception);
		}

		return currentVersion;
	}

	public void downloadLatestVersion() {
		if (!checkUpdate()) return;

		try {
			// get the url to download the resource from
			URL url = new URI("").toURL();
			// establish the connection by having a new channel
			ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());

			// specify the output path for the JAR file
			String outputPath = "";

			// create a file output stream to write the downloaded file
			FileOutputStream fileOutputStream = new FileOutputStream(outputPath);

			fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

			fileOutputStream.close();
			readableByteChannel.close();
		} catch (FileNotFoundException exception) {
			logger.error("Unable to find the new file.", exception);
		} catch (Exception exception) {
			logger.error("Unable to download the new file.", exception);
		}
	}

	public void start() {
		if (this.repeatingTimer == null) return;

		logger.info("Checking for updates");
		task();
		this.repeatingTimer.start(true);
	}

	private void task() {
		String newVersion     = getLatestVersion();
		String currentVersion = plugin.getDescription().getVersion();

		if (newVersion != null && !newVersion.equals(currentVersion)) {
			ChatUtil.sendToOperators(checkPermission, String.format(
					"The current version is %s, please update to the newest version available: %s", currentVersion,
					newVersion));
		} else {
			if (!checked.get()) {
				ChatUtil.sendToOperators(checkPermission, "The plugin is up to date.");
				checked.set(true);
			}
		}
	}

	private boolean checkUpdate() {
		return resourceId > -1;
	}

}
