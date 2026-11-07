package org.luckyraven.gangland.command.sub.rank;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.permission.Permission;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.util.ChatUtil;
import org.mockito.MockedStatic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the comma and arrow separated lists printed by {@code /glw rank info}, {@code /glw rank list} and
 * {@code /glw rank traverse}: no trailing separator, nothing for an empty list, the text "null" for a null name.
 */
@DisplayName("Rank info, list and traverse - separated lists")
class RankJoinedTextTest {

	private BukkitStatics bukkit;
	private MockedStatic<Settings> settings;
	private RankManager   rankManager;
	private CommandSender sender;

	@BeforeEach
	void setUp() {
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				if (path.equals(Messages.RANK_INFO_PRIMARY.getPath())) return "P:%rank%|%parent%";
				if (path.equals(Messages.RANK_INFO_SECONDARY.getPath())) return "S:%permissions%";
				if (path.equals(Messages.RANK_LIST_SECONDARY.getPath())) return "L:%ranks%";
				return path;
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
		// Messages colour through Settings.getMoneySymbol(), which only a loaded settings.yml sets.
		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		bukkit      = BukkitStatics.install();
		rankManager = mock(RankManager.class);
		sender      = mock(CommandSender.class);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
	}

	@Test
	@DisplayName("info joins the permission nodes and the child rank names with commas, nothing trailing")
	void info_joinsPermissionsAndChildren() {
		Argument info = new RankInfoCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), rankManager)
				.getNode().getChildren().get(0).getData();

		Rank none = new Rank("none", 1);
		Rank one  = new Rank("one", 2, List.of(new Permission(1, "a.b")));
		Rank many = new Rank("many", 3, List.of(new Permission(1, "a.b"), new Permission(2, "c.d"),
		                                         new Permission(3, "e.f")));
		one.getNode().add(new Rank("kid", 4).getNode());
		many.getNode().add(new Rank("kidA", 5).getNode());
		many.getNode().add(new Rank("kidB", 6).getNode());
		many.getNode().add(new Rank(null, 7).getNode());
		for (Rank rank : List.of(none, one, many)) {
			when(rankManager.get(rank.getName())).thenReturn(rank);
		}

		info.executeArgument(sender, new String[]{"rank", "info", "none"});
		verifySentEndingWith("P:none|");
		verifySentEndingWith("S:");

		info.executeArgument(sender, new String[]{"rank", "info", "one"});
		verifySentEndingWith("P:one|kid");
		verifySentEndingWith("S:a.b");

		info.executeArgument(sender, new String[]{"rank", "info", "many"});
		verifySentEndingWith("P:many|kidA, kidB, null");
		verifySentEndingWith("S:a.b, c.d, e.f");
	}

	@Test
	@DisplayName("list joins every rank name with commas, nothing trailing, nothing for no ranks")
	void list_joinsRankNames() {
		Map<Integer, Rank> ranks = new LinkedHashMap<>();
		when(rankManager.getRanks()).thenReturn(ranks);
		Argument list = new RankListCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), rankManager);

		list.executeArgument(sender, new String[]{"rank", "list"});
		verifySentEndingWith("L:");

		ranks.put(1, new Rank("member", 1));
		list.executeArgument(sender, new String[]{"rank", "list"});
		verifySentEndingWith("L:member");

		ranks.put(2, new Rank("officer", 2));
		ranks.put(3, new Rank(null, 3));
		list.executeArgument(sender, new String[]{"rank", "list"});
		verifySentEndingWith("L:member, officer, null");
	}

	@Test
	@DisplayName("traverse joins the rank names in tree order with a coloured arrow, nothing trailing")
	void traverse_joinsRankNamesWithArrows() {
		Tree<Rank> tree = new Tree<>();
		when(rankManager.getRankTree()).thenReturn(tree);
		Argument traverse = new RankTraverseCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class),
		                                            rankManager);

		traverse.executeArgument(sender, new String[]{"rank", "traverse"});
		verify(sender).sendMessage(ChatUtil.color(""));

		Rank member = new Rank("member", 1);
		tree.add(member.getNode());
		traverse.executeArgument(sender, new String[]{"rank", "traverse"});
		verify(sender).sendMessage(ChatUtil.color("&emember"));

		Rank officer = new Rank("officer", 2);
		Rank unnamed = new Rank(null, 3);
		member.getNode().add(officer.getNode());
		officer.getNode().add(unnamed.getNode());
		traverse.executeArgument(sender, new String[]{"rank", "traverse"});
		verify(sender).sendMessage(ChatUtil.color("&emember &b-> &eofficer &b-> &enull"));
	}

	/** The shipped lines carry the command prefix in front, so the lists are checked by what they end with. */
	private void verifySentEndingWith(String tail) {
		verify(sender).sendMessage(argThat((String message) -> message.endsWith(tail)));
	}

}
