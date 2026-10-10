package org.luckyraven.gangland.gang.support;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link GangMessages} over the shipped {@code gang/gang_messages.yml}, so the W2-P2 tests read the real player text.
 * Callers must stub {@code Settings.getLanguagePicked()} when they mock {@code Settings} statically.
 */
public final class W2P2Messages {

	private W2P2Messages() {
	}

	public static GangMessages shipped(Path dir) throws IOException {
		Path file = dir.resolve("gang_messages.yml");
		try (InputStream in = W2P2Messages.class.getResourceAsStream("/gang/gang_messages.yml")) {
			if (in == null) {
				throw new IOException("missing classpath resource /gang/gang_messages.yml");
			}
			Files.write(file, in.readAllBytes());
		}
		JavaPlugin  plugin      = PluginMocks.plugin(dir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
		return new GangMessages(fileManager);
	}

}
