package org.luckyraven.gangland.copsncrooks.command.jail;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * gi=91: an empty jail registry used to send only the header, then iterate zero cells - no feedback at all, unlike
 * the sibling {@code TurfListCommand} pattern which short-circuits to an {@code *_EMPTY} message.
 */
@DisplayName("JailListCommand")
class JailListCommandTest {

	@TempDir
	Path tempDir;

	private BukkitStatics bukkit;

	@BeforeEach
	void setUp() throws IOException {
		bukkit = BukkitStatics.install(); // SubArgument registers a Bukkit permission on construction

		// Messages.JAIL_LIST_EMPTY/_HEADER are Type.PREFIX -> GanglandChatUtil.color() unconditionally substitutes
		// %money_symbol% from Settings, NPE-ing on every color() call until Settings is initialized (gangland-impl's
		// SettingsFixture equivalent isn't reachable from this module's test tree).
		Files.writeString(tempDir.resolve("settings.yml"), "Money_Symbol: '$'\n", StandardCharsets.UTF_8);
		JavaPlugin  settingsPlugin = PluginMocks.plugin(tempDir);
		FileHandler handler       = new FileHandler(settingsPlugin, tempDir.resolve("settings.yml").toFile());
		FileManager fileManager   = new FileManager(settingsPlugin);
		fileManager.addFile(handler, false);
		new Settings(fileManager).initialize();

		Map<String, String> strings = Map.of("Jail.List_Empty", "no jails", "Jail.List_Header", "header");
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return strings.get(path);
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("an empty registry sends the empty message, not a bare header")
	void action_noCells_sendsEmptyMessageNotBareHeader() {
		JailRegistry registry = mock(JailRegistry.class);
		when(registry.getCells()).thenReturn(List.of());

		CommandSender sender = mock(CommandSender.class);

		new JailListCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), registry)
				.executeArgument(sender, new String[0]);

		verify(sender).sendMessage(Messages.JAIL_LIST_EMPTY.toString());
		verify(sender, never()).sendMessage(Messages.JAIL_LIST_HEADER.toString());
	}
}
