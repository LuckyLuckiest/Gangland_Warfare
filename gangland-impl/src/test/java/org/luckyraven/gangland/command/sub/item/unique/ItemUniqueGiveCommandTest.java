package org.luckyraven.gangland.command.sub.item.unique;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.unique.UniqueItem;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("/glw item unique give <name> <amount>")
class ItemUniqueGiveCommandTest {

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
				              .withString("Errors.Must_Be_Numbers", "BAD")
				              .withString("Commands.Item.Unique.Gave", "GAVE %name% %amount%")
				              .withString("Commands.Item.Unique.Invalid", "INVALID %name%"));
	}

	@Test
	@DisplayName("a negative amount that rounds to a negative stack count gives nothing instead of throwing")
	@SuppressWarnings("unchecked")
	void negativeAmount_doesNotThrow() {
		JavaPlugin          plugin      = mock(JavaPlugin.class);
		Tree<Argument>      tree        = new Tree<>();
		Argument            parent      = new Argument(plugin, "unique", tree);
		tree.add(parent.getNode());
		UserManager<Player> userManager = mock(UserManager.class);
		UniqueItemAddon     addon       = mock(UniqueItemAddon.class);
		UniqueItem          unique      = mock(UniqueItem.class);
		ItemStack           sample      = mock(ItemStack.class);

		when(sample.getMaxStackSize()).thenReturn(64);
		when(unique.buildItem(any(Player.class))).thenReturn(sample);
		when(addon.getUniqueItem("badge")).thenReturn(unique);

		Argument command = new ItemUniqueGiveCommand(plugin, tree, parent, userManager, addon);
		Argument name    = command.getNode().getChildren().get(0).getData();
		Argument amount  = name.getNode().getChildren().get(0).getData();

		Player          player    = mock(Player.class);
		User<Player>    user      = mock(User.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
		when(userManager.getUser(player)).thenReturn(user);

		assertDoesNotThrow(() -> amount.getAction().accept(null, player,
		                                                   new String[]{"item", "unique", "give", "badge", "-100"}));

		verify(inventory, never()).addItem(any(ItemStack[].class));
	}
}
