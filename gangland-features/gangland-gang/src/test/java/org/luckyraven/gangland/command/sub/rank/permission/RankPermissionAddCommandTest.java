package org.luckyraven.gangland.command.sub.rank.permission;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * GR-37: {@code /glw rank permission add <rank> <node>} accepted any string and never refreshed online members.
 * Now an unknown node is refused, and an accepted node reaches the online members of that rank at once.
 */
@DisplayName("RankPermissionAddCommand - node validation and online refresh")
class RankPermissionAddCommandTest {

	private BukkitStatics       bukkit;
	private JavaPlugin          plugin;
	private RankManager         rankManager;
	private PermissionManager   permissionManager;
	private MemberManager       memberManager;
	private UserManager<Player> userManager;
	private CommandSender       sender;

	@BeforeEach
	void setUp() {
		bukkit            = BukkitStatics.install();
		plugin            = mock(JavaPlugin.class);
		rankManager       = mock(RankManager.class);
		permissionManager = mock(PermissionManager.class);
		memberManager     = mock(MemberManager.class);
		userManager       = mock(UserManager.class);
		sender            = mock(CommandSender.class);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("an unknown node is refused with the invalid-permission message and nothing is persisted")
	void unknownNode_isRefused() {
		when(rankManager.get("officer")).thenReturn(new Rank("officer", 2));
		when(rankManager.permissionExists("gangland.gang.foo")).thenReturn(false);

		add("officer", "gangland.gang.foo");

		verify(sender).sendMessage(Messages.INVALID_RANK_PERMISSION.toString());
		verify(rankManager, never()).addPermission(any(Rank.class), anyString());
		verify(memberManager, never()).applyRankPermissionChange(any(), anyString(), anyBoolean());
	}

	@Test
	@DisplayName("a registered gang node is accepted, persisted, and flushed onto the online members of that rank")
	void gangInvite_isAcceptedAndFlushesOnlineMembers() {
		Rank officer = new Rank("officer", 2);
		when(rankManager.get("officer")).thenReturn(officer);
		when(rankManager.permissionExists(GangPermissions.INVITE)).thenReturn(true);
		when(rankManager.addPermission(officer, GangPermissions.INVITE)).thenReturn(true);

		UUID   uuid   = UUID.randomUUID();
		Member member = new Member(uuid);
		member.setRank(officer);
		when(memberManager.getMembersByRank(officer)).thenReturn(List.of(member));

		Player       player = mock(Player.class);
		User<Player> user   = mock(User.class);
		bukkit.statics().when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
		when(userManager.getUser(player)).thenReturn(user);

		add("officer", GangPermissions.INVITE);

		verify(rankManager).addPermission(officer, GangPermissions.INVITE);
		verify(user).setPermission(GangPermissions.INVITE, true);
	}

	@Test
	@DisplayName("tab completion offers every gangland.gang.* node next to the permissions the server knows")
	void suggestions_includeTheGangNodes() {
		when(permissionManager.getPermissions()).thenReturn(Set.of("some.server.node"));

		List<String> suggestions = RankPermissionAddCommand.suggestions(permissionManager);

		assertTrue(suggestions.containsAll(GangPermissions.ALL));
		assertTrue(suggestions.contains("some.server.node"));
	}

	private void add(String rank, String node) {
		Argument add     = new RankPermissionAddCommand(plugin, new Tree<>(), mock(Argument.class), rankManager,
		                                                permissionManager, memberManager, userManager);
		Argument rankArg = optionalChild(add);
		Argument permArg = optionalChild(rankArg);
		permArg.executeArgument(sender, new String[]{"rank", "permission", "add", rank, node});
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
