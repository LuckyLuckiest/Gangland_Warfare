package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.nio.file.Path;
import java.util.Locale;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-934: the type argument was upper-cased with the JVM default locale, so on a Turkish-locale server the {@code i} of
 * {@code hospital} became a dotted {@code İ} and the lookup fell back to the waypoint's old type.
 */
@DisplayName("WaypointTypeCommand - type parsing under a Turkish default locale")
class WaypointTypeCommandLocaleTest {

	@TempDir
	static Path tempDir;

	private Locale previous;

	@BeforeAll
	static void bootstrap() {
		BukkitRegistryFixture.install();
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPermissions()).thenReturn(java.util.Set.of());
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);

		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider().withString("Errors.Prefix", ""));
	}

	@BeforeEach
	void turkishLocale() {
		previous = Locale.getDefault();
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));
	}

	@AfterEach
	void restoreLocale() {
		Locale.setDefault(previous);
	}

	@Test
	@DisplayName("a lower-case i in the type name still finds the type")
	@SuppressWarnings("unchecked")
	void lowerCaseI_stillFindsTheType() {
		JavaPlugin          plugin          = mock(JavaPlugin.class);
		Tree<Argument>      tree            = new Tree<>();
		Argument            parent          = new Argument(plugin, "waypoint", tree);
		tree.add(parent.getNode());
		UserManager<Player> userManager     = mock(UserManager.class);
		WaypointManager     waypointManager = mock(WaypointManager.class);
		Player              player          = mock(Player.class);
		User<Player>        user            = mock(User.class);
		Waypoint            waypoint        = mock(Waypoint.class);

		when(userManager.getUser(player)).thenReturn(user);
		when(waypointManager.getSelected(player)).thenReturn(waypoint);
		when(waypoint.getType()).thenReturn(Waypoint.WaypointType.SPAWN);

		Argument command = new WaypointTypeCommand(plugin, tree, parent, userManager, waypointManager);
		Argument value   = command.getNode().getChildren().get(0).getData();

		value.getAction().accept(value, player, new String[]{"waypoint", "type", "hospital"});

		verify(waypoint).setType(Waypoint.WaypointType.HOSPITAL);
	}
}
