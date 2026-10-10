package org.luckyraven.gangland.turf.support;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds a real {@link FileManager} over YAML written into a temp folder, so the config classes read exactly what a
 * server would read. {@link #shipped(String)} returns a bundled default from the module's classpath.
 */
public final class YamlFixture {

	private YamlFixture() {
	}

	/** Registers {@code <name>.yml} with the given content and returns the manager. */
	public static FileManager fileManager(Path dir, String name, String yaml) throws IOException {
		Path file = dir.resolve(name + ".yml");
		Files.writeString(file, yaml, StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(dir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
		return fileManager;
	}

	/** The bundled default of a turf resource, e.g. {@code /turf/turf_rules.yml}. */
	public static String shipped(String resource) throws IOException {
		try (InputStream in = YamlFixture.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IOException("missing classpath resource " + resource);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
