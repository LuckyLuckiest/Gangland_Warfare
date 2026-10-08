package org.luckyraven.gangland.copsncrooks.command;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every key of {@link CommandMessages.Key} is shipped in {@code copsncrooks/commands.yml} with the same text as its
 * in-code fallback. Reads the YAML directly, so no Settings is needed.
 */
class CommandMessagesTest {

	@Test
	void everyKeyIsShippedWithItsFallback() throws IOException {
		YamlConfiguration yaml = load("/copsncrooks/commands.yml");

		for (CommandMessages.Key key : CommandMessages.Key.values()) {
			assertTrue(yaml.isString(key.path), key.path + " is missing from commands.yml");
			assertEquals(key.fallback, yaml.getString(key.path), key.path);
		}
	}

	/** commands_es.yml replaces commands.yml whole when Settings.Language is es, so it must carry every key too. */
	@Test
	void everyKeyIsShippedInTheSpanishFile() throws IOException {
		YamlConfiguration spanish = load("/copsncrooks/commands_es.yml");

		for (CommandMessages.Key key : CommandMessages.Key.values())
			assertTrue(spanish.isString(key.path), key.path + " is missing from commands_es.yml");
	}

	private static YamlConfiguration load(String resource) throws IOException {
		try (InputStream in = CommandMessagesTest.class.getResourceAsStream(resource)) {
			assertNotNull(in, resource + " is not on the classpath");
			return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

}
