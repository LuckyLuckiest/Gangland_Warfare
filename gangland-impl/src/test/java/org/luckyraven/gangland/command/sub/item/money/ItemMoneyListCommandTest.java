package org.luckyraven.gangland.command.sub.item.money;

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
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyItem;
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
 * Characterises {@code /glw item money list}: the header, then one line with every variation id in map order,
 * {@code &b} before each id and {@code &7, } between ids (none after the last), colour-translated.
 */
@DisplayName("ItemMoneyListCommand - list output")
class ItemMoneyListCommandTest {

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
		                                       .withString("Commands.Item.Money.List_Header", "HEADER"));
	}

	private static MoneyItem item(String id) {
		MoneyItem item = mock(MoneyItem.class);
		when(item.getId()).thenReturn(id);
		return item;
	}

	private static InOrder run(Map<String, MoneyItem> variations, CommandSender sender) {
		JavaPlugin     plugin = mock(JavaPlugin.class);
		Tree<Argument> tree   = new Tree<>();
		Argument       parent = new Argument(plugin, "money", tree);
		tree.add(parent.getNode());
		MoneyAddon addon = mock(MoneyAddon.class);
		when(addon.getVariations()).thenReturn(variations);

		ItemMoneyListCommand command = new ItemMoneyListCommand(plugin, tree, parent, addon);
		command.getAction().accept(command, sender, new String[]{"item", "money", "list"});

		return inOrder(sender);
	}

	@Test
	@DisplayName("no variations: the header, then an empty line")
	void empty() {
		CommandSender sender = mock(CommandSender.class);

		InOrder order = run(new LinkedHashMap<>(), sender);

		order.verify(sender).sendMessage(contains("HEADER"));
		order.verify(sender).sendMessage(GanglandChatUtil.color(""));
	}

	@Test
	@DisplayName("one variation: no separator")
	void single() {
		CommandSender sender = mock(CommandSender.class);
		Map<String, MoneyItem> variations = new LinkedHashMap<>();
		variations.put("k1", item("bill_1"));

		InOrder order = run(variations, sender);

		order.verify(sender).sendMessage(contains("HEADER"));
		order.verify(sender).sendMessage(GanglandChatUtil.color("&bbill_1"));
	}

	@Test
	@DisplayName("several variations: ids in map order, separator only between them")
	void several() {
		CommandSender sender = mock(CommandSender.class);
		Map<String, MoneyItem> variations = new LinkedHashMap<>();
		variations.put("z", item("bill_5"));
		variations.put("a", item("bill_1"));
		variations.put("m", item("bill_10"));

		InOrder order = run(variations, sender);

		order.verify(sender).sendMessage(contains("HEADER"));
		order.verify(sender).sendMessage(GanglandChatUtil.color("&bbill_5&7, &bbill_1&7, &bbill_10"));
		verify(sender, times(2)).sendMessage(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	@DisplayName("a variation without an id prints the text 'null'")
	void nullId() {
		CommandSender sender = mock(CommandSender.class);
		Map<String, MoneyItem> variations = new LinkedHashMap<>();
		variations.put("a", item("bill_1"));
		variations.put("b", item(null));

		InOrder order = run(variations, sender);

		order.verify(sender).sendMessage(contains("HEADER"));
		order.verify(sender).sendMessage(GanglandChatUtil.color("&bbill_1&7, &bnull"));
	}
}
