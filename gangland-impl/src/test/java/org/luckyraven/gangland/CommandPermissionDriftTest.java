package org.luckyraven.gangland;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drift guard (T-307 follow-up): every command listed in the {@code commands.json} help files of core and the runtime
 * modules maps to a permission node ({@code gangland.command.<words>}). Each node must be declared in
 * {@code plugin.yml} (a conscious default) or sit at or under an explicit staff prefix below. A new player verb that
 * is neither fails here instead of shipping op-only by accident.
 */
@DisplayName("every help-listed command has a declared default or is explicitly staff-only")
class CommandPermissionDriftTest {

	/** Roots (and nodes) that are staff-only on purpose, or never checked; everything beneath them is exempt. */
	private static final List<String> STAFF = List.of(
			"gangland.command.rank", "gangland.command.module", "gangland.command.permissions",
			"gangland.command.debug", "gangland.command.economy", "gangland.command.item",
			"gangland.command.level", "gangland.command.shop", "gangland.command.option",
			"gangland.command.reload", "gangland.command.cop", "gangland.command.jail",
			"gangland.command.civilian", "gangland.command.banker", "gangland.command.trader",
			"gangland.command.bounty", "gangland.command.cuff", "gangland.command.uncuff",
			"gangland.command.waypoint", "gangland.command.perm", "gangland.command.filter",
			"gangland.command.fuel", "gangland.command.update", "gangland.command.lootchest",
			"gangland.command.spawn",
			// help entries that name an alias (tp, desc), an unchecked ConfirmArgument-style child, or no longer
			// registered command (dealer, kit, warp, safewand): they carry no node of their own
			"gangland.command.tp", "gangland.command.gang.desc", "gangland.command.gang.display.remove",
			"gangland.command.dealer", "gangland.command.kit", "gangland.command.warp",
			"gangland.command.safewand");

	@Test
	void everyCommandNodeIsDeclaredOrStaffOnly() throws IOException {
		Set<String>  declared = declaredNodes();
		List<String> staff    = new ArrayList<>(STAFF);
		staff.addAll(PluginYmlPermissionsTest.OP_ONLY);
		staff.addAll(PluginYmlPermissionsTest.ADMIN);

		Set<String> undeclared = new TreeSet<>();
		for (String node : helpNodes()) {
			boolean covered = declared.contains(node) || staff.stream()
					.anyMatch(prefix -> node.equals(prefix) || node.startsWith(prefix + "."));
			if (!covered) undeclared.add(node);
		}

		assertTrue(undeclared.isEmpty(),
		           "commands.json lists commands whose node is neither in plugin.yml nor staff-only: " + undeclared);
	}

	@SuppressWarnings("unchecked")
	private static Set<String> declaredNodes() throws IOException {
		try (InputStream stream = CommandPermissionDriftTest.class.getResourceAsStream("/plugin.yml")) {
			Map<String, Object> root = new Yaml().load(stream);
			return new TreeSet<>(((Map<String, Object>) root.get("permissions")).keySet());
		}
	}

	private static Set<String> helpNodes() throws IOException {
		List<Path> files = new ArrayList<>();
		files.add(Path.of("src/main/resources/commands.json"));
		try (Stream<Path> modules = Files.list(Path.of("../gangland-features"))) {
			modules.map(module -> module.resolve("src/main/resources/commands.json"))
			       .filter(Files::exists)
			       .forEach(files::add);
		}

		Set<String> nodes = new TreeSet<>();
		for (Path file : files) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				for (Map.Entry<String, JsonElement> entry : new JsonParser().parse(reader).getAsJsonObject().entrySet()) {
					JsonElement value = entry.getValue();
					if (!value.isJsonObject()) continue;
					JsonObject object = value.getAsJsonObject();
					if (!object.has("usage")) continue;
					String node = nodeOf(object.get("usage").getAsString());
					if (node != null) nodes.add(node);
				}
			}
		}
		return nodes;
	}

	/** {@code /glw gang delete <x>} becomes {@code gangland.command.gang.delete}; the node stops at the first argument. */
	private static String nodeOf(String usage) {
		String[] words = usage.trim().split("\\s+");
		if (words.length < 2 || !words[0].startsWith("/glw")) return null;

		StringBuilder node = new StringBuilder("gangland.command");
		for (int i = 1; i < words.length; i++) {
			String word = words[i];
			if (word.equals("help")) return null;
			if (word.startsWith("<") || word.startsWith("[") || word.startsWith("(")) break;
			node.append('.').append(word.split("[|]")[0].toLowerCase());
		}
		return node.toString().equals("gangland.command") ? null : node.toString();
	}
}
