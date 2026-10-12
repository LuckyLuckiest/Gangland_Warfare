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
			"gangland.command.turf.quartermaster",
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
			"gangland.command.gang.ally",
			"gangland.command.gang.ally.request",
			"gangland.command.gang.ally.accept",
			"gangland.command.gang.ally.reject",
			"gangland.command.gang.ally.pending",
			"gangland.command.gang.ally.pending.cancel",
			"gangland.command.gang.ally.abandon",
			"gangland.command.gang.accept",
			"gangland.command.gang.decline",
			"gangland.command.gang.kick",
			"gangland.command.gang.promote",
			"gangland.command.gang.demote",
			"gangland.command.gang.chat",
			"gangland.command.gang.join",
			"gangland.command.gang.recruiting",
			"gangland.command.gang.bounty",
			"gangland.command.gang.delete",
			"gangland.command.gang.rename",
			"gangland.command.gang.transfer",
			"gangland.command.gang.color",
			"gangland.command.gang.display",
			"gangland.command.gang.description",
			"gangland.command.gang.ally",
			"gangland.command.gang.ally.abandon",
			"gangland.command.gang.ally.request",
			"gangland.command.gang.ally.accept",
			"gangland.command.gang.ally.reject",
			"gangland.command.gang.ally.pending",
			"gangland.command.gang.ally.pending.cancel",
			"gangland.command.bank",
			"gangland.command.bank.balance",
			"gangland.command.bank.deposit",
			"gangland.command.bank.withdraw",
			"gangland.command.bank.menu",
			"gangland.command.bank.create",
			"gangland.command.balance",
			"gangland.command.teleport",
			"gangland.command.respawn",
			"gangland.command.wanted",
			"gangland.command.contact",
			"gangland.command.filter",
			"gangland.command.resource",
			"gangland.command.car",
			"gangland.command.car.list",
			"gangland.command.car.info",
			"gangland.command.grapple",
			"gangland.command.jetpack",
			"gangland.inventory.gang_stat",
			"gangland.inventory.gang_info",
			"gangland.inventory.alliance_stat",
			"gangland.command.map.zoom",
			"gangland.command.map.border",
			"gangland.command.gang.ally.pvp",
			"gangland.command.gang.chat.allies",
			"gangland.command.gang.upgrade",
			"gangland.command.gang.upgrade.confirm",
			"gangland.command.gang.safehouse",
			"gangland.command.gang.safehouse.set",
			"gangland.command.gang.safehouse.remove",
			"gangland.command.gang.safehouse.list",
			"gangland.command.gang.rally",
			"gangland.command.gang.rally.join",
			"gangland.command.gang.pvp",
			"gangland.command.gang.notice",
			"gangland.command.gang.ranking",
			"gangland.command.gang.profile",
			"gangland.command.gang.fight",
			"gangland.command.gang.fight.propose",
			"gangland.command.gang.fight.accept",
			"gangland.command.gang.fight.decline",
			"gangland.command.gang.fight.enlist",
			"gangland.command.gang.fight.drop",
			"gangland.command.gang.fight.info",
			"gangland.command.gang.fight.arenas",
			"gangland.command.gang.fight.stats",
			"gangland.inventory.player_profile",
			"gangland.inventory.gang_ranking");

	/** Children of gangland.turf.admin; never default true. */
	public static final List<String> ADMIN = List.of(
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

	static final List<String> ARENA = List.of(
			"gangland.command.arena",
			"gangland.command.arena.create",
			"gangland.command.arena.corner",
			"gangland.command.arena.spawn",
			"gangland.command.arena.exit",
			"gangland.command.arena.size",
			"gangland.command.arena.finish",
			"gangland.command.arena.show",
			"gangland.command.arena.list",
			"gangland.command.arena.tp",
			"gangland.command.arena.on",
			"gangland.command.arena.off",
			"gangland.command.arena.delete",
			"gangland.command.arena.reload");

	/** Children of gangland.admin.gang; never default true (a default-true gangland.gang.* would bypass rank gates). */
	static final List<String> ADMIN_GANG = List.of(
			"gangland.admin.gang.spy",
			"gangland.admin.gang.force_rank",
			"gangland.gang.balance.others",
			"gangland.gang.ranking.wealth",
			"gangland.gang.fight.bypass_commands");

	/** Staff verbs under otherwise-open roots (T-307); never default true. */
	public static final List<String> OP_ONLY = List.of(
			"gangland.command.wanted.add",
			"gangland.command.wanted.remove",
			"gangland.command.wanted.clear",
			"gangland.command.car.give",
			"gangland.command.grapple.give",
			"gangland.command.jetpack.give",
			"gangland.command.balance.admin",
			"gangland.command.teleport.cooldown_bypass",
			"gangland.command.waypoint",
			"gangland.command.waypoint.create",
			"gangland.command.waypoint.delete",
			"gangland.command.bank.resetcap");

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
		for (String node : OP_ONLY) {
			assertFalse(permissions.containsKey(node) && isTrue(defaultOf(node)), node + " must not default to true");
		}
		for (String node : permissions.keySet()) {
			if (node.startsWith("gangland.command.rank") || node.startsWith("gangland.command.option")) {
				assertFalse(isTrue(defaultOf(node)), node + " must not default to true");
			}
		}
	}

	@Test
	@DisplayName("gangland.arena.admin defaults to op and owns every arena verb")
	void arenaAdmin_isOpAndOwnsTheArenaVerbs() {
		Map<String, Object> arena = castMap(permissions.get("gangland.arena.admin"));

		assertTrue(arena != null, "gangland.arena.admin must exist");
		assertEquals("op", String.valueOf(arena.get("default")));
		Map<String, Object> children = castMap(arena.get("children"));
		assertTrue(children != null, "gangland.arena.admin must list its children");
		for (String node : ARENA) {
			assertTrue(isTrue(children.get(node)), node + " must be a child of gangland.arena.admin");
		}
	}

	@Test
	@DisplayName("gangland.admin.gang defaults to op and owns every gang staff node")
	void gangAdmin_isOpAndOwnsTheStaffNodes() {
		Map<String, Object> staff = castMap(permissions.get("gangland.admin.gang"));

		assertTrue(staff != null, "gangland.admin.gang must exist");
		assertEquals("op", String.valueOf(staff.get("default")));
		Map<String, Object> children = castMap(staff.get("children"));
		assertTrue(children != null, "gangland.admin.gang must list its children");
		for (String node : ADMIN_GANG) {
			assertTrue(isTrue(children.get(node)), node + " must be a child of gangland.admin.gang");
		}
	}

	@Test
	@DisplayName("no rank-gated gangland.gang.* or gangland.admin.* key defaults to true")
	void noRankGateNode_defaultsTrue() {
		for (String node : permissions.keySet()) {
			if (node.startsWith("gangland.gang.") || node.startsWith("gangland.admin.")) {
				assertFalse(isTrue(defaultOf(node)), node + " must not default to true");
			}
		}
	}

	@Test
	@DisplayName("gangland.command.player defaults to true and lists every player node as a child")
	void playerParent_coversEveryPlayerNode() {
		Map<String, Object> parent = castMap(permissions.get("gangland.command.player"));

		assertTrue(parent != null, "gangland.command.player must exist");
		assertTrue(isTrue(parent.get("default")), "gangland.command.player must default to true");
		Map<String, Object> children = castMap(parent.get("children"));
		assertTrue(children != null, "gangland.command.player must list its children");
		for (String node : PLAYER) {
			assertTrue(isTrue(children.get(node)), node + " must be a child of gangland.command.player");
		}
	}

	@Test
	@DisplayName("softdepend lists dynmap and WorldGuard as optional soft dependencies")
	void softdepend_listsDynmapAndWorldGuard() throws IOException {
		try (InputStream stream = PluginYmlPermissionsTest.class.getResourceAsStream("/plugin.yml")) {
			Map<String, Object> root = new Yaml().load(new InputStreamReader(stream, StandardCharsets.UTF_8));
			List<?> soft = (List<?>) root.get("softdepend");

			assertTrue(soft != null, "plugin.yml must declare softdepend");
			assertTrue(soft.contains("dynmap"), "softdepend must list dynmap");
			assertTrue(soft.contains("WorldGuard"), "softdepend must list WorldGuard");
		}
	}
}
