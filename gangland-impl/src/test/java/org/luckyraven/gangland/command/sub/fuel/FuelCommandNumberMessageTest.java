package org.luckyraven.gangland.command.sub.fuel;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.item.fuel.Fuel;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins gi=54 (P3): the "amount" sub-argument's {@code NumberFormatException} handler in
 * {@code FuelAddCommand}/{@code FuelRemoveCommand}/{@code FuelDefuelCommand}/{@code FuelRefuelCommand} sent
 * {@code Messages.MUST_BE_NUMBERS.toString()} raw, leaving the {@code %command%} token in the
 * {@code Errors.Must_Be_Numbers} template unresolved.
 */
@DisplayName("Fuel commands — amount argument names the bad token (gi=54)")
class FuelCommandNumberMessageTest {

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void bootstrap() {
		BukkitRegistryFixture.install();
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPermissions()).thenReturn(java.util.Set.of());
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);

		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider().withString("Errors.Must_Be_Numbers", "&a%command% &7must be numbers."));
	}

	private static Argument amountArgument(Argument command) {
		return command.getNode().getChildren().get(0).getData();
	}

	private static Player playerWithFuelItem() {
		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getItemInMainHand()).thenReturn(mock(ItemStack.class));
		return player;
	}

	@Test
	@DisplayName("FuelAddCommand: non-numeric amount names the bad token")
	void fuelAddCommand_nonNumericAmount_namesToken() {
		JavaPlugin           plugin      = mock(JavaPlugin.class);
		Tree<Argument>       tree        = new Tree<>();
		Argument             parent      = new Argument(plugin, "fuel", tree);
		tree.add(parent.getNode());
		UserManager<Player>  userManager = mock(UserManager.class);

		FuelAddCommand command = new FuelAddCommand(plugin, tree, parent, userManager);

		Player       player = playerWithFuelItem();
		User<Player> user   = mock(User.class);
		when(userManager.getUser(player)).thenReturn(user);

		try (var fuel = mockStatic(Fuel.class)) {
			fuel.when(() -> Fuel.hasFuelCapacity(org.mockito.ArgumentMatchers.any())).thenReturn(true);

			amountArgument(command).getAction().accept(null, player, new String[]{"fuel", "add", "not-a-number"});
		}

		var captor = forClass(String.class);
		verify(user).sendMessage(captor.capture());
		assertTrue(captor.getValue().contains("not-a-number"), "message must name the bad token");
		assertFalse(captor.getValue().contains("%command%"), "the %command% placeholder must be resolved");
	}

	@Test
	@DisplayName("FuelRemoveCommand: non-numeric amount names the bad token")
	void fuelRemoveCommand_nonNumericAmount_namesToken() {
		JavaPlugin           plugin      = mock(JavaPlugin.class);
		Tree<Argument>       tree        = new Tree<>();
		Argument             parent      = new Argument(plugin, "fuel", tree);
		tree.add(parent.getNode());
		UserManager<Player>  userManager = mock(UserManager.class);

		FuelRemoveCommand command = new FuelRemoveCommand(plugin, tree, parent, userManager);

		Player       player = playerWithFuelItem();
		User<Player> user   = mock(User.class);
		when(userManager.getUser(player)).thenReturn(user);

		try (var fuel = mockStatic(Fuel.class)) {
			fuel.when(() -> Fuel.hasFuelCapacity(org.mockito.ArgumentMatchers.any())).thenReturn(true);

			amountArgument(command).getAction().accept(null, player, new String[]{"fuel", "remove", "not-a-number"});
		}

		var captor = forClass(String.class);
		verify(user).sendMessage(captor.capture());
		assertTrue(captor.getValue().contains("not-a-number"), "message must name the bad token");
		assertFalse(captor.getValue().contains("%command%"), "the %command% placeholder must be resolved");
	}

	@Test
	@DisplayName("FuelDefuelCommand: non-numeric amount names the bad token")
	void fuelDefuelCommand_nonNumericAmount_namesToken() {
		JavaPlugin           plugin      = mock(JavaPlugin.class);
		Tree<Argument>       tree        = new Tree<>();
		Argument             parent      = new Argument(plugin, "fuel", tree);
		tree.add(parent.getNode());
		UserManager<Player>  userManager = mock(UserManager.class);

		FuelDefuelCommand command = new FuelDefuelCommand(plugin, tree, parent, userManager);

		Player       player = playerWithFuelItem();
		User<Player> user   = mock(User.class);
		when(userManager.getUser(player)).thenReturn(user);

		try (var fuel = mockStatic(Fuel.class)) {
			fuel.when(() -> Fuel.hasFuelCapacity(org.mockito.ArgumentMatchers.any())).thenReturn(true);

			amountArgument(command).getAction().accept(null, player, new String[]{"fuel", "defuel", "not-a-number"});
		}

		var captor = forClass(String.class);
		verify(user).sendMessage(captor.capture());
		assertTrue(captor.getValue().contains("not-a-number"), "message must name the bad token");
		assertFalse(captor.getValue().contains("%command%"), "the %command% placeholder must be resolved");
	}

	@Test
	@DisplayName("FuelRefuelCommand: non-numeric amount names the bad token")
	void fuelRefuelCommand_nonNumericAmount_namesToken() {
		JavaPlugin           plugin      = mock(JavaPlugin.class);
		Tree<Argument>       tree        = new Tree<>();
		Argument             parent      = new Argument(plugin, "fuel", tree);
		tree.add(parent.getNode());
		UserManager<Player>  userManager = mock(UserManager.class);

		FuelRefuelCommand command = new FuelRefuelCommand(plugin, tree, parent, userManager);

		Player       player = playerWithFuelItem();
		User<Player> user   = mock(User.class);
		when(userManager.getUser(player)).thenReturn(user);

		try (var fuel = mockStatic(Fuel.class)) {
			fuel.when(() -> Fuel.hasFuelCapacity(org.mockito.ArgumentMatchers.any())).thenReturn(true);

			amountArgument(command).getAction().accept(null, player, new String[]{"fuel", "refuel", "not-a-number"});
		}

		var captor = forClass(String.class);
		verify(user).sendMessage(captor.capture());
		assertTrue(captor.getValue().contains("not-a-number"), "message must name the bad token");
		assertFalse(captor.getValue().contains("%command%"), "the %command% placeholder must be resolved");
	}
}
