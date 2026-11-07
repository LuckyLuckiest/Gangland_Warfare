package org.luckyraven.gangland.command.sub.rank;

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
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pins the rank-name completer of {@code /glw rank delete <name>} and {@code /glw rank info <name>}. */
@DisplayName("RankDeleteCommand and RankInfoCommand - rank name tab completion")
class RankNameCompleterTest {

	private final Map<Integer, Rank> ranks = new LinkedHashMap<>();

	private BukkitStatics bukkit;
	private RankManager   rankManager;
	private CommandSender sender;

	@BeforeEach
	void setUp() {
		bukkit      = BukkitStatics.install();
		rankManager = mock(RankManager.class);
		sender      = mock(CommandSender.class);
		when(rankManager.getRanks()).thenReturn(ranks);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("every completer lists the rank names in the rank map's order and reads the map at completion time")
	void completers_listTheRankNamesInOrder() {
		List<Argument> completers = completers();
		rank("member", 1);
		rank("officer", 2);
		rank("owner", 3);

		for (Argument completer : completers) {
			assertEquals(List.of("member", "officer", "owner"), completer.getArgumentString(sender));
		}

		rank("captain", 4);
		for (Argument completer : completers) {
			assertEquals(List.of("member", "officer", "owner", "captain"), completer.getArgumentString(sender));
		}
	}

	@Test
	@DisplayName("with no ranks every completer suggests the <name> placeholder")
	void completers_withoutRanksSuggestThePlaceholder() {
		for (Argument completer : completers()) {
			assertEquals(List.of("<name>"), completer.getArgumentString(sender));
		}
	}

	private List<Argument> completers() {
		Argument delete = new RankDeleteCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), rankManager,
		                                        mock(RepositoryRegistry.class), mock(MemberManager.class));
		Argument info = new RankInfoCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), rankManager);

		return List.of(optionalChild(delete), optionalChild(info));
	}

	private void rank(String name, int id) {
		ranks.put(id, new Rank(name, id));
	}

	private static Argument optionalChild(Argument command) {
		return command.getNode()
		              .getChildren()
		              .stream()
		              .map(Tree.Node::getData)
		              .filter(OptionalArgument.class::isInstance)
		              .findFirst()
		              .orElseThrow();
	}

}
