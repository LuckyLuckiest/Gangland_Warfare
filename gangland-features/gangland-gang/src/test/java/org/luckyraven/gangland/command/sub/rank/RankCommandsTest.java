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
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Drives {@code /glw rank delete <name>} and {@code /glw rank info <name>} through their real argument actions.
 */
@DisplayName("Rank commands - delete guard and info output")
class RankCommandsTest {

	private BukkitStatics bukkit;
	private RankManager   rankManager;
	private MemberManager memberManager;
	private CommandSender sender;

	@BeforeEach
	void setUp() throws ReflectiveOperationException {
		// Messages colour through Settings.getMoneySymbol(), which only a loaded settings.yml sets.
		Field moneySymbol = Settings.class.getDeclaredField("moneySymbol");
		moneySymbol.setAccessible(true);
		moneySymbol.set(null, "$");
		GangSettings.bind(new FakeGangSettingsContract());
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return switch (path) {
					case "Commands.Rank.Info.Secondary" -> "%permissions%";
					case "Commands.Rank.Remove.In_Use" -> "%rank%";
					default -> null;
				};
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
		bukkit = BukkitStatics.install();

		rankManager   = mock(RankManager.class);
		memberManager = mock(MemberManager.class);
		sender        = mock(CommandSender.class);
		when(memberManager.getMembers()).thenReturn(Map.of());
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("the configured Head and Tail ranks cannot be deleted")
	void delete_headOrTailRank_isRefused() {
		for (String name : List.of("member", "owner")) {
			rank(name, name.length());

			delete(name);

			verify(sender).sendMessage(Messages.RANK_REMOVE_IN_USE.toString().replace("%rank%", name));
		}
	}

	@Test
	@DisplayName("a rank still held by a member cannot be deleted - it would soft-lock them out of leave and kick")
	void delete_rankHeldByAMember_isRefused() {
		Rank officer = rank("officer", 3);
		Member holder = new Member(UUID.randomUUID());
		holder.setRank(new Rank("officer", 3)); // a reload hands members a different instance of the same rank
		when(memberManager.getMembers()).thenReturn(Map.of(holder.getUuid(), holder));

		delete(officer.getName());

		verify(sender).sendMessage(Messages.RANK_REMOVE_IN_USE.toString().replace("%rank%", "officer"));
	}

	@Test
	@DisplayName("a rank with another rank above it cannot be deleted - it would cut that rank out of the tree")
	void delete_rankWithRanksAboveIt_isRefused() {
		Rank officer = rank("officer", 3);
		officer.getNode().add(new Rank("captain", 4).getNode());

		delete(officer.getName());

		verify(sender).sendMessage(Messages.RANK_REMOVE_IN_USE.toString().replace("%rank%", "officer"));
	}

	@Test
	@DisplayName("GR-26: rank info lists permission nodes, not Permission.toString() debug output")
	void info_listsPermissionNodes() {
		Rank officer = new Rank("officer", 3, List.of(new Permission(3, "gangland.gang.withdraw")));
		when(rankManager.get("officer")).thenReturn(officer);

		Argument info = optionalChild(new RankInfoCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class),
		                                                  rankManager));
		info.executeArgument(sender, new String[]{"rank", "info", "officer"});

		ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
		verify(sender, atLeastOnce()).sendMessage(sent.capture());
		assertTrue(sent.getAllValues().stream().anyMatch(message -> message.contains("gangland.gang.withdraw")));
		assertFalse(sent.getAllValues().stream().anyMatch(message -> message.contains("Permission{")));
	}

	private void delete(String name) {
		Argument delete = optionalChild(new RankDeleteCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class),
		                                                      rankManager, mock(RepositoryRegistry.class),
		                                                      memberManager));
		delete.executeArgument(sender, new String[]{"rank", "delete", name});
	}

	private Rank rank(String name, int id) {
		Rank rank = new Rank(name, id);
		when(rankManager.get(name)).thenReturn(rank);
		return rank;
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
