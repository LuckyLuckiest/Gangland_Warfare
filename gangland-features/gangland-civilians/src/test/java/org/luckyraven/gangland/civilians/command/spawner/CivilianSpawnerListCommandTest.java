package org.luckyraven.gangland.civilians.command.spawner;

import com.google.gson.JsonParser;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.message.CivilianMessages;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * gi=91: an empty spawner registry used to send only the header, then iterate zero spawners - no feedback at all,
 * unlike the sibling {@code TurfListCommand} pattern which short-circuits to an {@code *_EMPTY} message.
 */
@DisplayName("CivilianSpawnerListCommand")
class CivilianSpawnerListCommandTest {

	private BukkitStatics bukkit;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install(); // SubArgument registers a Bukkit permission on construction
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("an empty registry sends the empty message, not a bare header")
	void action_noSpawners_sendsEmptyMessageNotBareHeader() {
		CivilianSpawnManager manager = mock(CivilianSpawnManager.class);
		when(manager.getSpawners()).thenReturn(List.of());

		CivilianMessages messages = mock(CivilianMessages.class);
		when(messages.spawnerListEmpty()).thenReturn("no spawners");
		when(messages.spawnerListHeader()).thenReturn("header");

		CommandSender sender = mock(CommandSender.class);

		new CivilianSpawnerListCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), manager, messages)
				.executeArgument(sender, new String[0]);

		verify(sender).sendMessage("no spawners");
		verify(sender, never()).sendMessage("header");
	}

	@Test
	void spawnerSetUsage_namesTheTypeIdArgument() throws Exception {
		try (var in = getClass().getClassLoader().getResourceAsStream("commands.json")) {
			@SuppressWarnings("deprecation") // parseReader is newer than the 1.16.5 floor's Gson
			String usage = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8))
			                               .getAsJsonObject().getAsJsonObject("civilian_spawner_set")
			                               .get("usage").getAsString();
			assertTrue(usage.contains("<typeId>"), usage);
		}
	}
}
