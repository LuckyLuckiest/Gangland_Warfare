package org.luckyraven.gangland;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the player and turf-admin permission defaults in {@code plugin.yml} (CM-37, TF-30, 0.17.0). Read with SnakeYAML
 * directly: permission nodes contain dots, which Bukkit's {@code YamlConfiguration} would split into sections.
 */
@DisplayName("plugin.yml - player and turf-admin permission defaults (CM-37, TF-30)")
class PluginYmlPermissionsTest {

	/** Nodes every player has (default true). Gang rank gates still apply inside the commands. */
	static final List<String> PLAYER = List.of(
			"gangland.command.main",
			"gangland.command.map",
			"gangland.command.map.auto",
			"gangland.command.map.hud",
			"gangland.command.map.text",
			"gangland.command.map.legend",
			"gangland.command.turf",
			"gangland.command.turf.info",
			"gangland.command.turf.list",
			"gangland.command.turf.rules",
			"gangland.command.gang",
			"gangland.command.gang.create",
			"gangland.command.gang.leave",
			"gangland.command.gang.list",
			"gangland.command.gang.info",
			"gangland.command.gang.members",
			"gangland.command.gang.balance",
			"gangland.command.gang.deposit",
			"gangland.command.gang.withdraw",
			"gangland.command.gang.invite",
			"gangland.command.gang.invite.cancel",
			"gangland.command.gang.accept",
			"gangland.command.gang.decline",
			"gangland.command.gang.kick",
			"gangland.command.gang.promote",
			"gangland.command.gang.demote",
			"gangland.command.gang.chat",
			"gangland.command.gang.join",
			"gangland.command.gang.recruiting",
			"gangland.command.gang.bounty",
			"gangland.inventory.gang_stat",
			"gangland.inventory.gang_info",
			"gangland.inventory.alliance_stat");

	/** Children of gangland.turf.admin; never default true. */
	static final List<String> ADMIN = List.of(
			"gangland.command.turf.wand",
			"gangland.command.turf.pos1",
			"gangland.command.turf.pos2",
			"gangland.command.turf.create",
			"gangland.command.turf.delete",
			"gangland.command.turf.setowner",
			"gangland.command.turf.show",
			"gangland.command.turf.status",
			"gangland.command.turf.select",
			"gangland.command.turf.tp",
			"gangland.command.turf.income",
			"gangland.command.turf.garrison",
			"gangland.command.turf.buff",
			"gangland.command.turf.powerupnpc",
			"gangland.command.turf.rename");

	private static Map<String, Object> permissions;

	@BeforeAll
	static void loadPluginYml() throws IOException {
		try (InputStream stream = PluginYmlPermissionsTest.class.getResourceAsStream("/plugin.yml")) {
			assertTrue(stream != null, "plugin.yml must be on the test classpath");
			Map<String, Object> root = new Yaml().load(new InputStreamReader(stream, StandardCharsets.UTF_8));
			permissions = castMap(root.get("permissions"));
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> castMap(Object value) {
		return (Map<String, Object>) value;
	}

	private static Object defaultOf(String node) {
		Map<String, Object> entry = castMap(permissions.get(node));
		return entry == null ? null : entry.get("default");
	}

	private static boolean isTrue(Object value) {
		return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
	}

	static Stream<String> playerNodes() {
		return PLAYER.stream();
	}

	@ParameterizedTest(name = "{0} defaults to true")
	@MethodSource("playerNodes")
	@DisplayName("every player node defaults to true")
	void playerNodes_defaultTrue(String node) {
		assertTrue(isTrue(defaultOf(node)), node + " must default to true");
	}

	@Test
	@DisplayName("T-226: gang_info menu defaults to true so members open it again; gangless players get onboarding via Open.Condition")
	void gangInfoMenu_isDefaultTrue() {
		assertTrue(isTrue(defaultOf("gangland.inventory.gang_info")),
				"gangland.inventory.gang_info must default to true; the menu's Open.Condition sends gangless players to onboarding");
	}

	@Test
	@DisplayName("gangland.turf.admin defaults to op and owns every admin verb")
	void turfAdmin_isOpAndOwnsTheAdminVerbs() {
		Map<String, Object> admin = castMap(permissions.get("gangland.turf.admin"));

		assertTrue(admin != null, "gangland.turf.admin must exist");
		assertEquals("op", String.valueOf(admin.get("default")));
		Map<String, Object> children = castMap(admin.get("children"));
		assertTrue(children != null, "gangland.turf.admin must list its children");
		for (String node : ADMIN) {
			assertTrue(isTrue(children.get(node)), node + " must be a child of gangland.turf.admin");
		}
	}

	@Test
	@DisplayName("no gangland.gang.* or gangland.admin.* node defaults to true")
	void noGangRankNodeDefaultsTrue() {
		for (String node : permissions.keySet()) {
			if (node.startsWith("gangland.gang.") || node.startsWith("gangland.admin.")) {
				assertFalse(isTrue(defaultOf(node)), node + " must not default to true");
			}
		}
	}

	@Test
	@DisplayName("admin verbs, rank and option commands never default to true")
	void adminVerbs_notDefaultTrue() {
		for (String node : ADMIN) {
			assertFalse(permissions.containsKey(node) && isTrue(defaultOf(node)), node + " must not default to true");
		}
		for (String node : permissions.keySet()) {
			if (node.startsWith("gangland.command.rank") || node.startsWith("gangland.command.option")) {
				assertFalse(isTrue(defaultOf(node)), node + " must not default to true");
			}
		}
	}
}
