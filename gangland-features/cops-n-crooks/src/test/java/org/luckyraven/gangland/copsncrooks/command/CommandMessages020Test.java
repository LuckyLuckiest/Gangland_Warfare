package org.luckyraven.gangland.copsncrooks.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lane f2-config: the replies of {@code /glw cop item} are real text (not blank), carry their placeholders, ship in
 * both languages, and the command is declared in the module's {@code commands.json}.
 */
@DisplayName("/glw cop item - messages and commands.json")
class CommandMessages020Test {

	private static final Map<CommandMessages.Key, List<String>> PLACEHOLDERS = Map.of(
			CommandMessages.Key.ITEM_GIVEN, List.of("%item%", "%target%"),
			CommandMessages.Key.ITEM_UNKNOWN, List.of("%item%", "%items%"),
			CommandMessages.Key.ITEM_NEEDS_PLAYER, List.of(),
			CommandMessages.Key.ITEM_NO_PERMISSION, List.of());

	@Test
	@DisplayName("each Item key has a non-blank fallback with its placeholders")
	void itemKeys_haveFallbacks() {
		for (Map.Entry<CommandMessages.Key, List<String>> entry : PLACEHOLDERS.entrySet()) {
			assertFalse(entry.getKey().fallback.isBlank(), entry.getKey() + " is blank");
			for (String placeholder : entry.getValue()) {
				assertTrue(entry.getKey().fallback.contains(placeholder), entry.getKey() + " lacks " + placeholder);
			}
		}
	}

	@Test
	@DisplayName("the Spanish commands_es.yml translates each Item key (differs from English, same placeholders)")
	void spanish_translatesItemKeys() throws IOException {
		YamlConfiguration es = load("/copsncrooks/commands_es.yml");
		YamlConfiguration en = load("/copsncrooks/commands.yml");

		for (Map.Entry<CommandMessages.Key, List<String>> entry : PLACEHOLDERS.entrySet()) {
			String path = entry.getKey().path;

			assertEquals(entry.getKey().fallback, en.getString(path), "en " + path);
			assertFalse(es.getString(path, "").isBlank(), "es " + path + " is blank");
			assertNotEquals(en.getString(path), es.getString(path), "es " + path + " is not translated");
			for (String placeholder : entry.getValue()) {
				assertTrue(es.getString(path).contains(placeholder), "es " + path + " lacks " + placeholder);
			}
		}
	}

	@Test
	@DisplayName("commands.json declares cop_item: usage with both items and [player], and a description")
	void commandsJson_declaresCopItem() throws IOException {
		try (InputStream in = CommandMessages020Test.class.getResourceAsStream("/commands.json")) {
			assertNotNull(in, "commands.json is not on the classpath");
			JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

			assertTrue(root.has("cop_item"), "cop_item is missing from commands.json");
			assertEquals("/glw cop item <item> [player]",
			             root.getAsJsonObject("cop_item").get("usage").getAsString());
			assertFalse(root.getAsJsonObject("cop_item").get("description").getAsString().isBlank());
		}
	}

	private static YamlConfiguration load(String resource) throws IOException {
		try (InputStream in = CommandMessages020Test.class.getResourceAsStream(resource)) {
			assertNotNull(in, resource + " is not on the classpath");
			return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}
}
