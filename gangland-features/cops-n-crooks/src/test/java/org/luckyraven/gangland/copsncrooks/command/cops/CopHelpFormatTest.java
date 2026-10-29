package org.luckyraven.gangland.copsncrooks.command.cops;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.command.data.CommandInformation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code /glw cop} help page: one row for setup and one for spawner, short placeholders, no pipe lists. */
@DisplayName("/glw cop help page")
class CopHelpFormatTest {

	private static Map<String, CommandInformation> commands() throws IOException {
		try (InputStream in = CopHelpFormatTest.class.getResourceAsStream("/commands.json")) {
			assertNotNull(in, "commands.json is not on the classpath");
			JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

			Map<String, CommandInformation> map = new LinkedHashMap<>();
			root.entrySet().forEach(e -> map.put(e.getKey(), new CommandInformation(
					e.getValue().getAsJsonObject().get("usage").getAsString(),
					e.getValue().getAsJsonObject().get("description").getAsString())));
			return map;
		}
	}

	private static long rowsStartingWith(List<CommandInformation> page, String prefix, String except) {
		return page.stream().map(CommandInformation::usage).filter(u -> u.startsWith(prefix) && !u.equals(except)).count();
	}

	@Test
	@DisplayName("the parent page shows one setup row and one spawner row, not every child")
	void parentPage_oneRowPerGroup() throws IOException {
		List<CommandInformation> page = CopCommand.parentPage(commands());
		List<String>             usages = page.stream().map(CommandInformation::usage).toList();

		assertTrue(usages.contains("/glw cop setup <action>"), usages.toString());
		assertTrue(usages.contains("/glw cop spawner <action>"), usages.toString());
		assertEquals(0, rowsStartingWith(page, "/glw cop setup ", "/glw cop setup <action>"), usages.toString());
		assertEquals(0, rowsStartingWith(page, "/glw cop spawner ", "/glw cop spawner <action>"), usages.toString());
	}

	@Test
	@DisplayName("the children keep their own commands.json rows")
	void children_stayDocumented() throws IOException {
		Map<String, CommandInformation> commands = commands();

		for (String key : List.of("cop_setup_wand", "cop_setup_mode", "cop_setup_save", "cop_setup_list",
		                          "cop_setup_remove", "cop_setup_tp", "cop_setup_link", "cop_spawner_set",
		                          "cop_spawner_remove", "cop_spawner_list", "cop_spawner_info",
		                          "cop_spawner_teleport")) {
			assertTrue(commands.containsKey(key), key);
		}
	}

	@Test
	@DisplayName("the operand rows use short placeholders")
	void rows_useShortPlaceholders() throws IOException {
		Map<String, CommandInformation> commands = commands();

		assertEquals("/glw cop setup mode <mode>", commands.get("cop_setup_mode").usage());
		assertEquals("/glw cop setup list [kind]", commands.get("cop_setup_list").usage());
		assertEquals("/glw cop setup remove <kind> <id>", commands.get("cop_setup_remove").usage());
		assertEquals("/glw cop setup tp <kind> <id>", commands.get("cop_setup_tp").usage());
		assertEquals("/glw cop item <item> [player]", commands.get("cop_item").usage());
	}
}
