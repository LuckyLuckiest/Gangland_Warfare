package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.permission.PermissionManager;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Characterises the tab-completion sources the waypoint select/delete/teleport commands hand to their
 * {@code OptionalArgument}: the visible names (gang waypoints first, then the permitted ones, a waypoint that is both
 * listed twice) and the display-to-id map (a name shared by several waypoints becomes {@code name:usedId}). The three
 * commands carry identical copies of this logic.
 */
@DisplayName("Waypoint select/delete/teleport - name completion")
class WaypointNameCompletionTest {

	private static final int GANG_ID = 7;

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void bootstrap() {
		BukkitRegistryFixture.install();
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPermissions()).thenReturn(java.util.Set.of());
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);

		InformationManager information = new InformationManager();
		information.processCommands();
		Command.setInformationManager(information); // TeleportCommand's constructor collects its help entries
	}

	private static Waypoint waypoint(String name, int gangId, String permission, int usedId) {
		Waypoint waypoint = mock(Waypoint.class);
		when(waypoint.getName()).thenReturn(name);
		when(waypoint.getGangId()).thenReturn(gangId);
		when(waypoint.getPermission()).thenReturn(permission);
		when(waypoint.getUsedId()).thenReturn(usedId);
		return waypoint;
	}

	/**
	 * Mixed fixture: "hq" is both the gang's and permitted; "dock" is gang-only; "plaza" and a second "plaza" are
	 * permitted-only (duplicate name); "vault" is neither.
	 */
	private static WaypointManager manager() {
		Map<Integer, Waypoint> all = new LinkedHashMap<>();
		all.put(1, waypoint("hq", GANG_ID, "perm.hq", 1));
		all.put(2, waypoint("dock", GANG_ID, "perm.dock", 2));
		all.put(3, waypoint("plaza", 0, "perm.plaza", 3));
		all.put(4, waypoint("plaza", 0, "perm.plaza", 4));
		all.put(5, waypoint("vault", 0, "perm.vault", 5));

		WaypointManager manager = mock(WaypointManager.class);
		when(manager.getWaypoints()).thenReturn(all);
		return manager;
	}

	@SuppressWarnings("unchecked")
	private static UserManager<Player> userManager(Player player, User<Player> user) {
		UserManager<Player> userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);
		return userManager;
	}

	@SuppressWarnings("unchecked")
	private static User<Player> gangUser() {
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(GANG_ID);
		return user;
	}

	@SuppressWarnings("unchecked")
	private static User<Player> gangless() {
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(false);
		return user;
	}

	private static Player playerWith(String... permissions) {
		Player player = mock(Player.class);
		for (String permission : permissions) {
			when(player.hasPermission(permission)).thenReturn(true);
		}
		return player;
	}

	private static OptionalArgument optionalOf(Argument command) {
		return command.getNode().getChildren().stream()
				.map(node -> node.getData())
				.filter(OptionalArgument.class::isInstance)
				.map(OptionalArgument.class::cast)
				.findFirst()
				.orElseThrow();
	}

	private static OptionalArgument select(UserManager<Player> userManager, WaypointManager manager) {
		JavaPlugin     plugin = mock(JavaPlugin.class);
		Tree<Argument> tree   = new Tree<>();
		Argument       parent = new Argument(plugin, "waypoint", tree);
		tree.add(parent.getNode());

		return optionalOf(new WaypointSelectCommand(plugin, tree, parent, userManager, manager));
	}

	private static OptionalArgument delete(UserManager<Player> userManager, WaypointManager manager) {
		JavaPlugin     plugin = mock(JavaPlugin.class);
		Tree<Argument> tree   = new Tree<>();
		Argument       parent = new Argument(plugin, "waypoint", tree);
		tree.add(parent.getNode());

		return optionalOf(new WaypointDeleteCommand(plugin, tree, parent, userManager, manager,
		                                            mock(GanglandDatabase.class), mock(PermissionManager.class)));
	}

	@SuppressWarnings("unchecked")
	private static OptionalArgument teleport(UserManager<Player> userManager, WaypointManager manager) {
		TeleportCommand command = new TeleportCommand(mock(JavaPlugin.class), userManager, manager);
		command.initializeArguments(); // the CommandManager calls this after construction

		return optionalOf(command.getArgument());
	}

	private interface Factory {

		OptionalArgument create(UserManager<Player> userManager, WaypointManager manager);
	}

	private static void assertNamesFor(Factory factory) {
		// gang member: gang waypoints first (hq, dock), then permitted ones in map order (hq again, plaza, plaza)
		Player           gangPlayer = playerWith("perm.hq", "perm.plaza");
		OptionalArgument gang       = factory.create(userManager(gangPlayer, gangUser()), manager());
		assertEquals(List.of("hq", "dock", "hq", "plaza", "plaza"), gang.getArgumentString(gangPlayer));

		// no gang: only the permitted ones
		Player           soloPlayer = playerWith("perm.dock", "perm.vault");
		OptionalArgument solo       = factory.create(userManager(soloPlayer, gangless()), manager());
		assertEquals(List.of("dock", "vault"), solo.getArgumentString(soloPlayer));

		// a gang member with no permission still sees the gang waypoints
		Player           bare     = playerWith();
		OptionalArgument bareGang = factory.create(userManager(bare, gangUser()), manager());
		assertEquals(List.of("hq", "dock"), bareGang.getArgumentString(bare));

		// nothing visible: the OptionalArgument falls back to a single empty suggestion
		OptionalArgument nothing = factory.create(userManager(bare, gangless()), manager());
		assertEquals(List.of(""), nothing.getArgumentString(bare));

		// unknown user: null source, same empty suggestion
		OptionalArgument unknown = factory.create(userManager(bare, null), manager());
		assertEquals(List.of(""), unknown.getArgumentString(bare));
	}

	@Test
	@DisplayName("WaypointSelectCommand offers gang waypoints, then permitted ones")
	void select_names() {
		assertNamesFor(WaypointNameCompletionTest::select);
	}

	@Test
	@DisplayName("WaypointDeleteCommand offers gang waypoints, then permitted ones")
	void delete_names() {
		assertNamesFor(WaypointNameCompletionTest::delete);
	}

	@Test
	@DisplayName("TeleportCommand offers gang waypoints, then permitted ones")
	void teleport_names() {
		assertNamesFor(WaypointNameCompletionTest::teleport);
	}

	private static void assertMapFor(Factory factory) {
		// gang member with hq+plaza permissions: the list is hq, dock, hq, plaza#3, plaza#4, so "hq" is counted twice
		Player           gangPlayer = playerWith("perm.hq", "perm.plaza");
		OptionalArgument gang       = factory.create(userManager(gangPlayer, gangUser()), manager());
		assertEquals("1", gang.getActualValue("hq:1", gangPlayer), "a repeated name maps as name:usedId");
		assertEquals("hq", gang.getActualValue("hq", gangPlayer), "the plain repeated name is not a key");
		assertEquals("2", gang.getActualValue("dock", gangPlayer), "a unique name keeps its plain display name");
		assertEquals("3", gang.getActualValue("plaza:3", gangPlayer));
		assertEquals("4", gang.getActualValue("plaza:4", gangPlayer));
		assertEquals("vault", gang.getActualValue("vault", gangPlayer), "not visible: falls back to the display text");

		// no gang, permitted dock+vault: plain names map to their ids
		Player           solo     = playerWith("perm.dock", "perm.vault");
		OptionalArgument soloArg  = factory.create(userManager(solo, gangless()), manager());
		assertEquals("2", soloArg.getActualValue("dock", solo));
		assertEquals("5", soloArg.getActualValue("vault", solo));
		assertEquals("hq", soloArg.getActualValue("hq", solo));

		// unknown user: no map, the display text comes back as typed
		Player           bare    = playerWith();
		OptionalArgument unknown = factory.create(userManager(bare, null), manager());
		assertEquals("dock", unknown.getActualValue("dock", bare));
	}

	@Test
	@DisplayName("WaypointSelectCommand maps display names to ids, repeated names as name:usedId")
	void select_map() {
		assertMapFor(WaypointNameCompletionTest::select);
	}

	@Test
	@DisplayName("WaypointDeleteCommand maps display names to ids, repeated names as name:usedId")
	void delete_map() {
		assertMapFor(WaypointNameCompletionTest::delete);
	}
}
