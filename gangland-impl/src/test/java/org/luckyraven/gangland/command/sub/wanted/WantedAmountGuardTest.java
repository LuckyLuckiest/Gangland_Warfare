package org.luckyraven.gangland.command.sub.wanted;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * {@code /glw wanted add|remove <amount>} must refuse a zero or negative amount: {@code remove -10} raised the level
 * and {@code add -3} lowered it, each reported with the opposite verb.
 */
@DisplayName("/glw wanted add|remove <amount> - amount guard")
class WantedAmountGuardTest {

	@TempDir
	Path dir;

	private BukkitStatics       bukkit;
	private JavaPlugin          plugin;
	private UserManager<Player> userManager;
	private Player              player;
	private Wanted              wanted;
	private Tree<Argument>      tree;
	private Argument            parent;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		SettingsFixture.initializeMinimal(dir);
		Messages.init(new FakeMessageProvider().withString("Errors.Must_Be_Numbers", "MUST-BE-NUMBERS %command%"));

		plugin      = mock(JavaPlugin.class);
		userManager = mock(UserManager.class);
		player      = mock(Player.class);
		wanted      = new Wanted(plugin, 1, 5);
		tree        = new Tree<>();
		parent      = new Argument(plugin, "wanted", tree);
		tree.add(parent.getNode());

		User<Player> user = mock(User.class);
		when(user.getWanted()).thenReturn(wanted);
		when(userManager.getUser(player)).thenReturn(user);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@ParameterizedTest(name = "remove {0} at level 3 is refused")
	@ValueSource(strings = {"-10", "0"})
	void remove_nonPositiveAmount_refused(String amount) {
		wanted.setLevel(3);

		run(new WantedRemoveCommand(plugin, tree, parent, userManager), "remove", amount);

		assertEquals(3, wanted.getLevel(), "a refused remove must not change the level");
		verify(player).sendMessage(contains("MUST-BE-NUMBERS"));
	}

	@ParameterizedTest(name = "add {0} at level 3 is refused")
	@ValueSource(strings = {"-3", "0"})
	void add_nonPositiveAmount_refused(String amount) {
		wanted.setLevel(3);

		run(new WantedAddCommand(plugin, tree, parent, userManager), "add", amount);

		assertEquals(3, wanted.getLevel(), "a refused add must not change the level");
		verify(player).sendMessage(contains("MUST-BE-NUMBERS"));
	}

	private void run(Argument command, String action, String amount) {
		Argument amountArgument = command.getNode().getChildren().get(0).getData();

		amountArgument.executeArgument(player, new String[]{"wanted", action, amount});
	}

}
