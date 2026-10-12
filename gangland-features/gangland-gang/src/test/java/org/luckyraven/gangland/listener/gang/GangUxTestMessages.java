package org.luckyraven.gangland.listener.gang;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** {@link GangMessages} over the shipped gang/gang_messages.yml (callers stub Settings.getLanguagePicked()). */
final class GangUxTestMessages {

	private GangUxTestMessages() {
	}

	static GangMessages shipped(Path dir) throws IOException {
		try (InputStream in = GangUxTestMessages.class.getResourceAsStream("/gang/gang_messages.yml")) {
			return over(dir, in.readAllBytes());
		}
	}

	/** An upgraded server's gang_messages.yml: present, but without the keys a later release added. */
	static GangMessages empty(Path dir) throws IOException {
		return over(dir, new byte[0]);
	}

	private static GangMessages over(Path dir, byte[] content) throws IOException {
		Files.createDirectories(dir);
		Path file = dir.resolve("gang_messages.yml");
		Files.write(file, content);
		JavaPlugin  plugin      = PluginMocks.plugin(dir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
		return new GangMessages(fileManager);
	}

}
