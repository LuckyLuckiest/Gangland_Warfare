package org.luckyraven.gangland.copsncrooks.command.cops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.gangland.copsncrooks.item.CopsItems;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lane f2-config: {@code /glw cop item <police_scanner|scanner_map> [player]} (permission {@code gangland.cop.item}).
 * The console must name a player, an unknown item or an offline player is answered, a sender without the permission
 * gets nothing, and an item that does not fit is dropped at the target's feet.
 */
@DisplayName("/glw cop item")
class CopItemCommandTest {

	private BukkitStatics    bukkit;
	private CopsItems        items;
	private CommandMessages  messages;
	private CopItemCommand   command;
	private ItemStack        scanner;

	@BeforeEach
	void setUp() {
		bukkit   = BukkitStatics.install(); // SubArgument registers a Bukkit permission on construction
		items    = mock(CopsItems.class);
		messages = mock(CommandMessages.class);
		scanner  = mock(ItemStack.class);
		when(items.create("police_scanner")).thenReturn(scanner);
		when(messages.format(any(CommandMessages.Key.class), anyMap())).thenAnswer(
				invocation -> invocation.getArgument(0, CommandMessages.Key.class).name());

		command = new CopItemCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), items, messages);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("the node permission is exactly gangland.cop.item, not a suffix of the parent permission")
	void nodePermissionIsGanglandCopItem() {
		Argument parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.cop");

		CopItemCommand built = new CopItemCommand(mock(JavaPlugin.class), new Tree<>(), parent, items, messages);

		org.junit.jupiter.api.Assertions.assertEquals("gangland.cop.item", built.getPermission());
	}

	@Test
	@DisplayName("the console names an online player: that player's inventory receives the marked scanner")
	void givesNamedItem() {
		CommandSender console = sender(false, true);
		Player        bob     = onlinePlayer("Bob");
		PlayerInventory inv   = bob.getInventory();

		command.give(console, "police_scanner", "Bob");

		verify(inv).addItem(scanner);
		verify(console).sendMessage("ITEM_GIVEN");
	}

	@Test
	@DisplayName("a player without a target argument gives the item to himself")
	void playerGivesToSelf() {
		Player self = onlinePlayer("Alice");

		command.give(self, "police_scanner", null);

		verify(self.getInventory()).addItem(scanner);
		verify(self).sendMessage("ITEM_GIVEN");
	}

	@Test
	@DisplayName("the console without a player argument is told it needs one; nothing is created")
	void consoleNeedsPlayer() {
		CommandSender console = sender(false, true);

		command.give(console, "police_scanner", null);

		verify(console).sendMessage("ITEM_NEEDS_PLAYER");
		verify(items, never()).create(any());
	}

	@Test
	@DisplayName("an unknown or missing item id is answered with the usage text and nothing is given")
	void unknownItem() {
		CommandSender console = sender(false, true);
		Player        bob     = onlinePlayer("Bob");

		command.give(console, "jetpack", "Bob");

		verify(console).sendMessage("ITEM_UNKNOWN");
		verify(bob.getInventory(), never()).addItem(any(ItemStack[].class));
	}

	@Test
	@DisplayName("a missing item id is answered with the usage text (ITEM_USAGE), never Item.Unknown with an empty %item%")
	void missingItemId_answersUsage() {
		CommandSender console = sender(false, true);
		Player        bob     = onlinePlayer("Bob");

		command.give(console, null, "Bob");

		verify(console).sendMessage("ITEM_USAGE");
		verify(console, never()).sendMessage("ITEM_UNKNOWN");
		verify(bob.getInventory(), never()).addItem(any(ItemStack[].class));
	}

	@Test
	@DisplayName("a player who is not online is answered with Player_Not_Found")
	void offlinePlayer() {
		CommandSender console = sender(false, true);
		bukkit.statics().when(() -> Bukkit.getPlayerExact("Zed")).thenReturn(null);
		bukkit.statics().when(() -> Bukkit.getPlayer("Zed")).thenReturn(null);

		command.give(console, "police_scanner", "Zed");

		verify(messages).format(eq(CommandMessages.Key.PLAYER_NOT_FOUND), eq(Map.of("player", "Zed")));
		verify(console).sendMessage("PLAYER_NOT_FOUND");
		verify(items, never()).create(any());
	}

	@Test
	@DisplayName("a sender without gangland.cop.item gets the permission message and nothing is given")
	void permissionDenied() {
		Player denied = onlinePlayer("Eve");
		when(denied.hasPermission("gangland.cop.item")).thenReturn(false);

		command.give(denied, "police_scanner", null);

		verify(denied).sendMessage("ITEM_NO_PERMISSION");
		verify(denied.getInventory(), never()).addItem(any(ItemStack[].class));
		verify(items, never()).create(any());
	}

	@Test
	@DisplayName("what does not fit in the inventory is dropped at the target's feet; a missing world does not throw")
	void fullInventory_dropsLeftover() {
		Player bob = onlinePlayer("Bob");
		World  world = mock(World.class);
		Location at  = new Location(world, 1, 2, 3);
		when(bob.getLocation()).thenReturn(at);
		when(bob.getInventory().addItem(scanner)).thenReturn(new HashMap<>(Map.of(0, scanner)));

		command.give(sender(false, true), "police_scanner", "Bob");

		verify(world).dropItemNaturally(at, scanner);

		Player noWorld = onlinePlayer("Ghost");
		when(noWorld.getLocation()).thenReturn(new Location(null, 0, 0, 0));
		when(noWorld.getInventory().addItem(scanner)).thenReturn(new HashMap<>(Map.of(0, scanner)));

		command.give(sender(false, true), "police_scanner", "Ghost");
	}

	private CommandSender sender(boolean player, boolean permitted) {
		CommandSender sender = player ? mock(Player.class) : mock(CommandSender.class);
		when(sender.hasPermission("gangland.cop.item")).thenReturn(permitted);
		return sender;
	}

	private Player onlinePlayer(String name) {
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);
		when(player.isOnline()).thenReturn(true);
		when(player.hasPermission("gangland.cop.item")).thenReturn(true);
		when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
		bukkit.statics().when(() -> Bukkit.getPlayerExact(name)).thenReturn(player);
		bukkit.statics().when(() -> Bukkit.getPlayer(name)).thenReturn(player);
		return player;
	}
}
