package org.luckyraven.gangland.command.sub.shop;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.shop.ShopRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-934: shop keys were lowered with the JVM default locale, so on a Turkish-locale server an upper-case {@code I}
 * became a dotless {@code i} and {@code /glw shop create INVENTORY} failed the {@code [a-z0-9_]+} check.
 */
@DisplayName("ShopCreateCommand - key parsing under a Turkish default locale")
class ShopCreateCommandLocaleTest {

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
		Messages.init(new FakeMessageProvider()
				              .withString("Errors.Prefix", "")
				              .withString("Errors.Shop.Key_Invalid", "KEY-INVALID")
				              .withString("Commands.Shop.Created", "CREATED %shop%"));
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
	@DisplayName("an upper-case I in the key is lowered to a plain i")
	void upperCaseI_isLoweredToPlainI() throws IOException {
		JavaPlugin     plugin   = mock(JavaPlugin.class);
		Tree<Argument> tree     = new Tree<>();
		Argument       parent   = new Argument(plugin, "shop", tree);
		tree.add(parent.getNode());
		ShopRegistry   registry = mock(ShopRegistry.class);
		CommandSender  sender   = mock(CommandSender.class);

		Argument command = new ShopCreateCommand(plugin, tree, parent, registry);
		Argument key     = command.getNode().getChildren().get(0).getData();

		key.getAction().accept(key, sender, new String[]{"shop", "create", "INVENTORY"});

		verify(registry).createEmpty("inventory");
		verify(registry, never()).createEmpty("ınventory");
	}
}
