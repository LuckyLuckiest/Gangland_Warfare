package org.luckyraven.gangland.command.sub.economy;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.mockito.MockedStatic;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Characterises how the {@code amount} and {@code targetPlayer} OptionalArguments of the economy deposit, withdraw and
 * set commands resolve their target (through {@code EconomyCommand.resolveTarget}) and hand it to the apply step. An
 * unparseable amount is used so the apply step stops at its first message and no economy state is needed.
 */
@DisplayName("Economy deposit/withdraw/set - target resolution")
class EconomyTargetResolutionTest {

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void bootstrap() {
		BukkitRegistryFixture.install();
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPermissions()).thenReturn(java.util.Set.of());
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);

		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider()
				              .withString("Errors.Prefix", "")
				              .withString("Errors.Player.Not_Player", "NOT-PLAYER")
				              .withString("Errors.Player.Player_Not_Found", "NOT-FOUND %player%")
				              .withString("Errors.Must_Be_Numbers", "BAD %command%"));
	}

	private interface Factory {

		Argument create(JavaPlugin plugin, Tree<Argument> tree, Argument parent, UserManager<Player> userManager);
	}

	private static final Factory[] COMMANDS = {
			EconomyDepositCommand::new, EconomyWithdrawCommand::new, EconomySetCommand::new
	};

	/** The two nodes whose actions resolve a target: the amount node and the targetPlayer node under it. */
	private static Argument[] actions(Factory factory, UserManager<Player> userManager) {
		JavaPlugin     plugin = mock(JavaPlugin.class);
		Tree<Argument> tree   = new Tree<>();
		Argument       parent = new Argument(plugin, "economy", tree);
		tree.add(parent.getNode());

		Argument         command = factory.create(plugin, tree, parent, userManager);
		OptionalArgument amount  = (OptionalArgument) command.getNode().getChildren().get(0).getData();
		OptionalArgument target  = (OptionalArgument) amount.getNode().getChildren().get(0).getData();

		return new Argument[]{amount, target};
	}

	@SuppressWarnings("unchecked")
	private static UserManager<Player> userManagerMock() {
		return mock(UserManager.class);
	}

	private static String[] args(String... tail) {
		String[] base = {"economy", "cmd", "abc"};
		String[] all  = new String[base.length + tail.length];

		System.arraycopy(base, 0, all, 0, base.length);
		System.arraycopy(tail, 0, all, base.length, tail.length);
		return all;
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2})
	@DisplayName("no target argument and a console sender: NOT_PLAYER, no user lookup")
	void consoleWithoutTarget_isNotPlayer(int index) {
		UserManager<Player> userManager = userManagerMock();

		for (Argument action : actions(COMMANDS[index], userManager)) {
			CommandSender console = mock(CommandSender.class);

			action.executeArgument(console, args());

			verify(console).sendMessage(contains("NOT-PLAYER"));
			verify(console, times(1)).sendMessage(anyString());
		}
		verifyNoInteractions(userManager);
	}

	@SuppressWarnings("unchecked")
	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2})
	@DisplayName("no target argument and a player sender: the sender's own user reaches the apply step")
	void playerWithoutTarget_usesOwnUser(int index) {
		UserManager<Player> userManager = userManagerMock();

		for (Argument action : actions(COMMANDS[index], userManager)) {
			Player       self = mock(Player.class);
			User<Player> user = mock(User.class);
			when(userManager.getUser(self)).thenReturn(user);

			action.executeArgument(self, args());

			verify(self).sendMessage(contains("BAD abc"));
			verify(self, times(1)).sendMessage(anyString());
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2})
	@DisplayName("an unknown target name: PLAYER_NOT_FOUND names it, nothing is applied")
	void unknownTarget_isReported(int index) {
		UserManager<Player> userManager = userManagerMock();

		for (Argument action : actions(COMMANDS[index], userManager)) {
			CommandSender sender = mock(CommandSender.class);

			try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
				bukkit.when(() -> Bukkit.getPlayer("Bob")).thenReturn(null);

				action.executeArgument(sender, args("Bob"));
			}

			verify(sender).sendMessage(contains("NOT-FOUND Bob"));
			verify(sender, times(1)).sendMessage(anyString());
		}
		verifyNoInteractions(userManager);
	}

	@SuppressWarnings("unchecked")
	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2})
	@DisplayName("a known target: its user is looked up and reaches the apply step")
	void knownTarget_reachesApply(int index) {
		UserManager<Player> userManager = userManagerMock();

		for (Argument action : actions(COMMANDS[index], userManager)) {
			CommandSender sender = mock(CommandSender.class);
			Player        bob    = mock(Player.class);
			User<Player>  user   = mock(User.class);
			when(userManager.getUser(bob)).thenReturn(user);

			try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
				bukkit.when(() -> Bukkit.getPlayer("Bob")).thenReturn(bob);

				action.executeArgument(sender, args("Bob"));
			}

			verify(sender).sendMessage(contains("BAD abc"));
			verify(sender, times(1)).sendMessage(anyString());
			verify(userManager).getUser(bob);
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2})
	@DisplayName("a known player without a user: nothing is sent and nothing is applied")
	void knownTargetWithoutUser_isSilent(int index) {
		UserManager<Player> userManager = userManagerMock();

		for (Argument action : actions(COMMANDS[index], userManager)) {
			CommandSender sender = mock(CommandSender.class);
			Player        bob    = mock(Player.class);
			when(userManager.getUser(bob)).thenReturn(null);

			try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
				bukkit.when(() -> Bukkit.getPlayer("Bob")).thenReturn(bob);

				action.executeArgument(sender, args("Bob"));
			}

			verify(sender, never()).sendMessage(anyString());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("EconomyCommand.resolveTarget returns the looked-up user, or null with the message on failure")
	void resolveTarget_directly() {
		UserManager<Player> userManager = userManagerMock();
		CommandSender       console     = mock(CommandSender.class);
		Player              self        = mock(Player.class);
		Player              bob         = mock(Player.class);
		User<Player>        selfUser    = mock(User.class);
		User<Player>        bobUser     = mock(User.class);
		when(userManager.getUser(self)).thenReturn(selfUser);
		when(userManager.getUser(bob)).thenReturn(bobUser);

		assertSame(selfUser, EconomyCommand.resolveTarget(self, new String[]{"a", "b", "c"}, 3, userManager));
		assertNull(EconomyCommand.resolveTarget(console, new String[]{"a", "b", "c"}, 3, userManager));
		verify(console).sendMessage(contains("NOT-PLAYER"));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer("Bob")).thenReturn(bob);
			bukkit.when(() -> Bukkit.getPlayer("Nope")).thenReturn(null);

			// a target argument wins over the sender, even for a console sender
			assertSame(bobUser, EconomyCommand.resolveTarget(console, new String[]{"a", "b", "c", "Bob"}, 3, userManager));
			assertNull(EconomyCommand.resolveTarget(console, new String[]{"a", "b", "c", "Nope"}, 3, userManager));
		}
		verify(console).sendMessage(contains("NOT-FOUND Nope"));
	}
}
