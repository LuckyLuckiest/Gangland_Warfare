package org.luckyraven.gangland.command.sub.item.unique;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.unique.UniqueItem;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.mockito.InOrder;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterises {@code /glw item unique list}: the header, then one line with every unique item name in map order,
 * {@code &b} before each id and {@code &7, } between ids (none after the last), colour-translated.
 */
@DisplayName("ItemUniqueListCommand - list output")
class ItemUniqueListCommandTest {

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void bootstrap() {
		BukkitRegistryFixture.install();
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPermissions()).thenReturn(java.util.Set.of());
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);

		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider().withString("Errors.Prefix", "")
		                                       .withString("Commands.Item.Unique.List_Header", "HEADER"));
	}

	private static UniqueItem item(String id) {
		UniqueItem item = mock(UniqueItem.class);
		when(item.getName()).thenReturn(id);
		return item;
	}

	private static InOrder run(Map<String, UniqueItem> uniqueItems, CommandSender sender) {
		JavaPlugin     plugin = mock(JavaPlugin.class);
		Tree<Argument> tree   = new Tree<>();
		Argument       parent = new Argument(plugin, "unique", tree);
		tree.add(parent.getNode());
		UniqueItemAddon addon = mock(UniqueItemAddon.class);
		when(addon.getUniqueItems()).thenReturn(uniqueItems);

		ItemUniqueListCommand command = new ItemUniqueListCommand(plugin, tree, parent, addon);
		command.getAction().accept(command, sender, new String[]{"item", "unique", "list"});

		return inOrder(sender);
	}

	@Test
	@DisplayName("no uniqueItems: the header, then an empty line")
	void empty() {
		CommandSender sender = mock(CommandSender.class);

		InOrder order = run(new LinkedHashMap<>(), sender);

		order.verify(sender).sendMessage(contains("HEADER"));
		order.verify(sender).sendMessage(GanglandChatUtil.color(""));
	}

	@Test
	@DisplayName("one unique item: no separator")
	void single() {
		CommandSender sender = mock(CommandSender.class);
		Map<String, UniqueItem> uniqueItems = new LinkedHashMap<>();
		uniqueItems.put("k1", item("name_1"));

		InOrder order = run(uniqueItems, sender);

		order.verify(sender).sendMessage(contains("HEADER"));
		order.verify(sender).sendMessage(GanglandChatUtil.color("&bname_1"));
	}

	@Test
	@DisplayName("several uniqueItems: ids in map order, separator only between them")
	void several() {
		CommandSender sender = mock(CommandSender.class);
		Map<String, UniqueItem> uniqueItems = new LinkedHashMap<>();
		uniqueItems.put("z", item("name_5"));
		uniqueItems.put("a", item("name_1"));
		uniqueItems.put("m", item("name_10"));

		InOrder order = run(uniqueItems, sender);

		order.verify(sender).sendMessage(contains("HEADER"));
		order.verify(sender).sendMessage(GanglandChatUtil.color("&bname_5&7, &bname_1&7, &bname_10"));
		verify(sender, times(2)).sendMessage(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	@DisplayName("a unique item without a name prints the text 'null'")
	void nullId() {
		CommandSender sender = mock(CommandSender.class);
		Map<String, UniqueItem> uniqueItems = new LinkedHashMap<>();
		uniqueItems.put("a", item("name_1"));
		uniqueItems.put("b", item(null));

		InOrder order = run(uniqueItems, sender);

		order.verify(sender).sendMessage(contains("HEADER"));
		order.verify(sender).sendMessage(GanglandChatUtil.color("&bname_1&7, &bnull"));
	}
}
