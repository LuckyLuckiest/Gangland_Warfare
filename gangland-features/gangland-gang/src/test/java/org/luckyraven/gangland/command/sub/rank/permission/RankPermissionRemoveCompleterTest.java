package org.luckyraven.gangland.command.sub.rank.permission;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.permission.PermissionManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pins the rank-name completer of {@code /glw rank permission remove <rank> <permission>}. */
@DisplayName("RankPermissionRemoveCommand - rank name tab completion")
class RankPermissionRemoveCompleterTest {

	private final Map<Integer, Rank> ranks = new LinkedHashMap<>();

	private BukkitStatics bukkit;
	private RankManager   rankManager;
	private CommandSender sender;
	private Argument      rankArgument;

	@BeforeEach
	void setUp() {
		bukkit      = BukkitStatics.install();
		rankManager = mock(RankManager.class);
		sender      = mock(CommandSender.class);
		when(rankManager.getRanks()).thenReturn(ranks);

		Argument remove = new RankPermissionRemoveCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class),
		                                                  rankManager, mock(PermissionManager.class),
		                                                  mock(MemberManager.class));
		rankArgument = remove.getNode().getChildren().get(0).getData();
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("the completer lists the rank names in the rank map's order and reads the map at completion time")
	void completer_listsTheRankNamesInOrder() {
		rank("member", 1);
		rank("officer", 2);
		rank("owner", 3);

		assertEquals(List.of("member", "officer", "owner"), rankArgument.getArgumentString(sender));

		rank("captain", 4);
		assertEquals(List.of("member", "officer", "owner", "captain"), rankArgument.getArgumentString(sender));
	}

	@Test
	@DisplayName("with no ranks the completer suggests the <name> placeholder")
	void completer_withoutRanksSuggestsThePlaceholder() {
		assertEquals(List.of("<name>"), rankArgument.getArgumentString(sender));
	}

	private void rank(String name, int id) {
		ranks.put(id, new Rank(name, id));
	}

}
