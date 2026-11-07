package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the tab completion of {@code /glw gang promote <name>}: the names of the gang members the sender outranks, in
 * member order, or one empty string when there is nobody (or the sender cannot be resolved).
 */
@DisplayName("GangPromoteCommand - tab completion of promotable members")
class GangPromoteCompleterTest {

	private BukkitStatics bukkit;
	private UserManager<Player> userManager;
	private MemberManager       memberManager;
	private Player              actor;
	private User<Player>        actorUser;
	private Member              actorMember;
	private Gang                gang;
	private Argument            promote;
	private Rank                memberRank;
	private Rank                enforcerRank;
	private Rank                ownerRank;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();

		userManager   = mock(UserManager.class);
		memberManager = mock(MemberManager.class);
		RankManager rankManager = mock(RankManager.class);
		GangManager gangManager = mock(GangManager.class);

		actor     = mock(Player.class);
		actorUser = mock(User.class);
		when(actor.getUniqueId()).thenReturn(UUID.randomUUID());
		when(userManager.getUser(actor)).thenReturn(actorUser);
		when(actorUser.hasGang()).thenReturn(true);
		when(actorUser.getGangId()).thenReturn(1);

		actorMember = new Member(actor.getUniqueId());
		when(memberManager.getMember(actor.getUniqueId())).thenReturn(actorMember);
		OfflinePlayer boss = offline("Boss");
		bukkit.statics().when(() -> Bukkit.getOfflinePlayer(actor.getUniqueId())).thenReturn(boss);

		gang = mock(Gang.class);
		when(gangManager.getGang(1)).thenReturn(gang);

		memberRank   = new Rank("member", 1);
		enforcerRank = new Rank("enforcer", 2);
		ownerRank    = new Rank("owner", 3);
		memberRank.getNode().add(enforcerRank.getNode());
		enforcerRank.getNode().add(ownerRank.getNode());
		Tree<Rank> rankTree = new Tree<>();
		rankTree.add(memberRank.getNode());
		when(rankManager.getRankTree()).thenReturn(rankTree);

		promote = new GangPromoteCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), userManager,
		                                 memberManager, gangManager, rankManager).getNode()
		                                                                         .getChildren()
		                                                                         .get(0)
		                                                                         .getData();
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("an owner is offered every named member below them, in member order")
	void owner_isOfferedLowerNamedMembersInOrder() {
		actorMember.setRank(ownerRank);
		Member dave  = member(enforcerRank, "Dave");
		Member ghost = member(memberRank, null);
		Member bare  = member(null, "Bare");
		Member carl  = member(memberRank, "Carl");
		when(gang.getMembers()).thenReturn(List.of(actorMember, dave, ghost, bare, carl));

		assertEquals(List.of("Dave", "Carl"), promote.getArgumentString(actor));
	}

	@Test
	@DisplayName("an enforcer is offered only the members below the enforcer rank")
	void enforcer_isOfferedOnlyLowerMembers() {
		actorMember.setRank(enforcerRank);
		Member dave = member(enforcerRank, "Dave");
		Member carl = member(memberRank, "Carl");
		Member boss = member(ownerRank, "Owner");
		when(gang.getMembers()).thenReturn(List.of(boss, dave, carl));

		assertEquals(List.of("Carl"), promote.getArgumentString(actor));
	}

	@Test
	@DisplayName("nobody below the sender gives the single empty suggestion")
	void lowestRank_isOfferedOnlyTheEmptySuggestion() {
		actorMember.setRank(memberRank);
		Member boss = member(ownerRank, "Owner");
		when(gang.getMembers()).thenReturn(List.of(boss));

		assertEquals(List.of(""), promote.getArgumentString(actor));
	}

	@Test
	@DisplayName("a sender with no cached user, no member, no gang or no rank gets the empty suggestion")
	void unresolvableSender_isOfferedOnlyTheEmptySuggestion() {
		actorMember.setRank(ownerRank);
		Member carl = member(memberRank, "Carl");
		when(gang.getMembers()).thenReturn(List.of(carl));

		when(actorUser.hasGang()).thenReturn(false);
		assertEquals(List.of(""), promote.getArgumentString(actor));
		when(actorUser.hasGang()).thenReturn(true);

		when(memberManager.getMember(actor.getUniqueId())).thenReturn(null);
		assertEquals(List.of(""), promote.getArgumentString(actor));
		when(memberManager.getMember(actor.getUniqueId())).thenReturn(actorMember);

		actorMember.setRank(null);
		assertEquals(List.of(""), promote.getArgumentString(actor));
		actorMember.setRank(ownerRank);

		when(userManager.getUser(actor)).thenReturn(null);
		assertEquals(List.of(""), promote.getArgumentString(actor));
	}

	@Test
	@DisplayName("only members that pass the rank filter have their offline player looked up")
	void offlinePlayer_isLookedUpOnlyForOutrankedMembers() {
		actorMember.setRank(enforcerRank);
		Member carl = member(memberRank, "Carl");
		Member boss = member(ownerRank, "Owner");
		Member bare = member(null, "Bare");
		when(gang.getMembers()).thenReturn(List.of(boss, bare, carl));

		promote.getArgumentString(actor);

		bukkit.statics().verify(() -> Bukkit.getOfflinePlayer(carl.getUuid()), org.mockito.Mockito.times(1));
		bukkit.statics().verify(() -> Bukkit.getOfflinePlayer(boss.getUuid()), org.mockito.Mockito.never());
		bukkit.statics().verify(() -> Bukkit.getOfflinePlayer(bare.getUuid()), org.mockito.Mockito.never());
	}

	private Member member(Rank rank, String name) {
		Member member = new Member(UUID.randomUUID());
		member.setRank(rank);
		OfflinePlayer offlinePlayer = offline(name);
		bukkit.statics().when(() -> Bukkit.getOfflinePlayer(member.getUuid())).thenReturn(offlinePlayer);
		return member;
	}

	private OfflinePlayer offline(String name) {
		OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
		when(offlinePlayer.getName()).thenReturn(name);
		return offlinePlayer;
	}

}
