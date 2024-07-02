package me.luckyraven.updater;

import me.luckyraven.Gangland;
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

public class UpdateChecker {

	private final JavaPlugin plugin;
	private final int        resourceId;

	public UpdateChecker(JavaPlugin plugin, int resourceId) {
		this.plugin     = plugin;
		this.resourceId = resourceId;
	}

	public String getLatestVersion() {
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
			Gangland.getLog4jLogger().error("Unable to check for the latest version.", exception);
		}

		return plugin.getDescription().getVersion();
	}

	public void downloadLatestVersion() {
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

		} catch (Exception exception) {
			Gangland.getLog4jLogger().error("Unable to download the new file.", exception);
		}
	}

}
