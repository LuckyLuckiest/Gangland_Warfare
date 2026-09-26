package org.luckyraven.gangland.civilians.command.spawner;

import com.google.gson.JsonParser;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.message.CivilianMessages;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CivilianSpawnerListCommandTest {

	@Test
	@SuppressWarnings("unchecked")
	void emptySpawnerList_saysSo() {
		try (BukkitStatics ignored = BukkitStatics.install()) {
			Argument parent = mock(Argument.class);
			when(parent.getPermission()).thenReturn("gangland.civilian.spawner");
			CivilianSpawnManager manager = mock(CivilianSpawnManager.class);
			when(manager.getSpawners()).thenReturn(new ArrayList<>());
			CivilianMessages messages = mock(CivilianMessages.class);
			when(messages.spawnerListEmpty()).thenReturn("none");
			CommandSender sender = mock(CommandSender.class);

			new CivilianSpawnerListCommand(mock(JavaPlugin.class), mock(Tree.class), parent, manager, messages)
					.executeArgument(sender, new String[0]);

			verify(sender).sendMessage("none");
		}
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
