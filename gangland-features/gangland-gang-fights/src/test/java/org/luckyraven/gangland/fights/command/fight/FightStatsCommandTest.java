package org.luckyraven.gangland.fights.command.fight;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.fights.stat.FightHistory;
import org.luckyraven.gangland.fights.stat.FightTexts;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FightStatsCommand - /glw gang fight stats <gang>")
class FightStatsCommandTest {

	private MockedStatic<Bukkit> bukkit;

	@BeforeEach
	void setUp() {
		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		FightStatsCommand.bind(null);
	}

	@Test
	@DisplayName("the gang operand runs the stats action, so a named gang is looked up")
	void namedGang() {
		FightHistory history = mock(FightHistory.class);
		FightTexts   texts   = mock(FightTexts.class);
		GangManager  gangs   = mock(GangManager.class);
		Gang         gang    = mock(Gang.class);
		when(gang.getId()).thenReturn(7);
		when(gang.getName()).thenReturn("Wolves");
		when(gangs.getAll()).thenReturn(List.of(gang));
		when(texts.get("Stats.Header")).thenReturn("HEADER %gang%");
		when(texts.get("Stats.Line")).thenReturn("LINE");
		when(history.stats(7)).thenReturn(new org.luckyraven.gangland.fights.database.FightStats(7, 1, 0, 0));
		FightStatsCommand.bind(new FightStatsCommand.Services(history, texts, gangs, mock(MemberManager.class)));

		JavaPlugin     plugin = mock(JavaPlugin.class);
		Tree<Argument> tree   = new Tree<>();
		Argument       fight  = new Argument(plugin, "fight", tree, (a, s, args) -> {
		});
		FightStatsCommand stats = new FightStatsCommand(plugin, tree, fight);
		Argument          op0   = stats.getNode().getChildren().get(0).getData();

		CommandSender sender = mock(CommandSender.class);
		op0.getAction().accept(op0, sender, new String[]{"gang", "fight", "stats", "Wolves"});

		verify(sender).sendMessage(contains("HEADER Wolves"));
	}
}
