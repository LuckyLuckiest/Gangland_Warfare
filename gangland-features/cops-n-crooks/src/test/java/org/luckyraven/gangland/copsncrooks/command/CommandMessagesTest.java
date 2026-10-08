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
		try (InputStream in = getClass().getResourceAsStream("/copsncrooks/commands.yml")) {
			assertNotNull(in, "copsncrooks/commands.yml is not on the classpath");

			YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
					new InputStreamReader(in, StandardCharsets.UTF_8));

			for (CommandMessages.Key key : CommandMessages.Key.values()) {
				assertTrue(yaml.isString(key.path), key.path + " is missing from commands.yml");
				assertEquals(key.fallback, yaml.getString(key.path), key.path);
			}
		}
	}

}
