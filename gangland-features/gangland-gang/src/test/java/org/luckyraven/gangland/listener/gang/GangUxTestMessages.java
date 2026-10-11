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
		Path file = dir.resolve("gang_messages.yml");
		try (InputStream in = GangUxTestMessages.class.getResourceAsStream("/gang/gang_messages.yml")) {
			Files.write(file, in.readAllBytes());
		}
		JavaPlugin  plugin      = PluginMocks.plugin(dir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
		return new GangMessages(fileManager);
	}

}
