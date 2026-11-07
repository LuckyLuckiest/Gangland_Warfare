package org.luckyraven.gangland.command.sub.bank;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Characterises the admin-target branch of {@code /glw bank deposit|withdraw <amount> <player>} (the OptionalArgument
 * under the amount node): permission check, then the player lookup, the user lookup and the bank check, then the apply
 * step. An unparseable amount makes the apply step stop at its first message, so no bank state is needed.
 */
@DisplayName("Bank deposit/withdraw - admin target lookup")
class BankAdminTargetResolutionTest {

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
				              .withString("Errors.Permissions.Command", "NO-PERM")
				              .withString("Errors.Player.Player_Not_Found", "NOT-FOUND %player%")
				              .withString("Errors.Bank.Must_Create", "NO-BANK")
				              .withString("Errors.Must_Be_Numbers", "BAD %command%"));
	}

	private interface Factory {

		Argument create(JavaPlugin plugin, Tree<Argument> tree, Argument parent, UserManager<Player> userManager);
	}

	private static final Factory[] COMMANDS = {
			(plugin, tree, parent, userManager) -> new BankDepositCommand(plugin, tree, parent, userManager,
			                                                              mock(GanglandDatabase.class),
			                                                              mock(BankTiers.class)),
			(plugin, tree, parent, userManager) -> new BankWithdrawCommand(plugin, tree, parent, userManager,
			                                                               mock(GanglandDatabase.class))
	};

	/** The admin-target node: the OptionalArgument under the amount OptionalArgument. */
	private static Argument targetNode(Factory factory, UserManager<Player> userManager) {
		JavaPlugin     plugin = mock(JavaPlugin.class);
		Tree<Argument> tree   = new Tree<>();
		Argument       parent = new Argument(plugin, "bank", tree);
		tree.add(parent.getNode());

		Argument         command = factory.create(plugin, tree, parent, userManager);
		OptionalArgument amount  = (OptionalArgument) command.getNode().getChildren().get(0).getData();

		return amount.getNode().getChildren().get(0).getData();
	}

	@SuppressWarnings("unchecked")
	private static UserManager<Player> userManagerMock() {
		return mock(UserManager.class);
	}

	private static CommandSender admin() {
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission(BankCommand.ADMIN_PERMISSION)).thenReturn(true);
		return sender;
	}

	private static final String[] ARGS = {"bank", "cmd", "abc", "Bob"};

	@Test
	@DisplayName("without the admin permission: COMMAND_NO_PERM, no player lookup, even when the name is missing")
	void withoutPermission_isRefusedFirst() {
		for (Factory factory : COMMANDS) {
			UserManager<Player> userManager = userManagerMock();
			Argument            node        = targetNode(factory, userManager);
			CommandSender       sender      = mock(CommandSender.class);

			try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
				node.executeArgument(sender, ARGS);
				node.executeArgument(sender, new String[]{"bank", "cmd", "abc"});

				bukkit.verifyNoInteractions();
			}

			verify(sender, times(2)).sendMessage(contains("NO-PERM"));
			verify(sender, times(2)).sendMessage(anyString());
			verifyNoInteractions(userManager);
		}
	}

	@Test
	@DisplayName("admin with the name argument missing: the lookup reads args[3] and throws, nothing is sent")
	void adminWithoutName_throws() {
		for (Factory factory : COMMANDS) {
			UserManager<Player> userManager = userManagerMock();
			Argument            node        = targetNode(factory, userManager);
			CommandSender       sender      = admin();

			assertThrows(ArrayIndexOutOfBoundsException.class,
			             () -> node.executeArgument(sender, new String[]{"bank", "cmd", "abc"}));

			verify(sender, never()).sendMessage(anyString());
		}
	}

	@Test
	@DisplayName("admin and an unknown name: PLAYER_NOT_FOUND names it, the user is not looked up")
	void unknownName_isReported() {
		for (Factory factory : COMMANDS) {
			UserManager<Player> userManager = userManagerMock();
			Argument            node        = targetNode(factory, userManager);
			CommandSender       sender      = admin();

			try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
				bukkit.when(() -> Bukkit.getPlayer("Bob")).thenReturn(null);

				node.executeArgument(sender, ARGS);
			}

			verify(sender).sendMessage(contains("NOT-FOUND Bob"));
			verify(sender, times(1)).sendMessage(anyString());
			verifyNoInteractions(userManager);
		}
	}

	@Test
	@DisplayName("admin and a known player without a user: silent")
	void knownPlayerWithoutUser_isSilent() {
		for (Factory factory : COMMANDS) {
			UserManager<Player> userManager = userManagerMock();
			Argument            node        = targetNode(factory, userManager);
			CommandSender       sender      = admin();
			Player              bob         = mock(Player.class);
			when(userManager.getUser(bob)).thenReturn(null);

			try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
				bukkit.when(() -> Bukkit.getPlayer("Bob")).thenReturn(bob);

				node.executeArgument(sender, ARGS);
			}

			verify(userManager).getUser(bob);
			verify(sender, never()).sendMessage(anyString());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("admin and a target without a bank: MUST_CREATE_BANK goes to the sender")
	void targetWithoutBank_isReported() {
		for (Factory factory : COMMANDS) {
			UserManager<Player> userManager = userManagerMock();
			Argument            node        = targetNode(factory, userManager);
			CommandSender       sender      = admin();
			Player              bob         = mock(Player.class);
			User<Player>        user        = mock(User.class);
			when(userManager.getUser(bob)).thenReturn(user);
			when(user.hasBank()).thenReturn(false);

			try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
				bukkit.when(() -> Bukkit.getPlayer("Bob")).thenReturn(bob);

				node.executeArgument(sender, ARGS);
			}

			verify(sender).sendMessage(contains("NO-BANK"));
			verify(sender, times(1)).sendMessage(anyString());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("admin and a target with a bank: the amount is handed to the apply step (here: refused as BAD abc)")
	void targetWithBank_reachesApply() {
		for (Factory factory : COMMANDS) {
			UserManager<Player> userManager = userManagerMock();
			Argument            node        = targetNode(factory, userManager);
			CommandSender       sender      = admin();
			Player              bob         = mock(Player.class);
			User<Player>        user        = mock(User.class);
			when(userManager.getUser(bob)).thenReturn(user);
			when(user.hasBank()).thenReturn(true);

			try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
				bukkit.when(() -> Bukkit.getPlayer("Bob")).thenReturn(bob);

				node.executeArgument(sender, ARGS);
			}

			InOrder order = inOrder(sender, userManager, user);
			order.verify(sender).hasPermission(BankCommand.ADMIN_PERMISSION);
			order.verify(userManager).getUser(bob);
			order.verify(user).hasBank();
			order.verify(sender).sendMessage(contains("BAD abc"));
			verify(sender, times(1)).sendMessage(anyString());
		}
	}
}
