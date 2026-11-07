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
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how {@code /glw gang kick <name>} and {@code /glw gang demote <name>} find their target among the gang's
 * members: the first member whose offline name equals the typed name ignoring case, members with a null or empty
 * name never match, and the scan stops at the first match.
 */
@DisplayName("GangKickCommand and GangDemoteCommand - finding the target member by name")
class GangTargetLookupTest {

	private BukkitStatics bukkit;
	private UserManager<Player> userManager;
	private MemberManager       memberManager;
	private GangMembershipService membershipService;
	private Player              actor;
	private User<Player>        actorUser;
	private Member              actorMember;
	private Gang                gang;
	private Argument            kick;
	private Argument            demote;
	private Rank                lowRank;
	private Rank                highRank;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		GangSettings.bind(new FakeGangSettingsContract());
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return null;
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
		bukkit = BukkitStatics.install();

		userManager       = mock(UserManager.class);
		memberManager     = mock(MemberManager.class);
		membershipService = mock(GangMembershipService.class);
		RankManager rankManager = mock(RankManager.class);
		GangManager gangManager = mock(GangManager.class);

		actor     = mock(Player.class);
		actorUser = mock(User.class);
		when(actor.getUniqueId()).thenReturn(UUID.randomUUID());
		when(actor.hasPermission(GangPermissions.FORCE_RANK)).thenReturn(true);
		when(userManager.getUser(actor)).thenReturn(actorUser);
		when(actorUser.hasGang()).thenReturn(true);
		when(actorUser.getGangId()).thenReturn(7);

		actorMember = new Member(actor.getUniqueId());
		when(memberManager.getMember(actor.getUniqueId())).thenReturn(actorMember);

		gang = mock(Gang.class);
		when(gang.getId()).thenReturn(7);
		when(gangManager.getGang(7)).thenReturn(gang);

		lowRank  = new Rank("member", 1);
		highRank = new Rank("owner", 2);
		lowRank.getNode().add(highRank.getNode());
		Tree<Rank> rankTree = new Tree<>();
		rankTree.add(lowRank.getNode());
		when(rankManager.getRankTree()).thenReturn(rankTree);
		actorMember.setRank(highRank);

		when(membershipService.kick(any(Integer.class), any(UUID.class), any(UUID.class))).thenReturn(
				GangMembershipService.Result.fail("test.refused"));
		when(membershipService.text(any())).thenReturn("KICK REFUSED");

		kick = new GangKickCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), userManager,
		                           mock(UserManager.class), memberManager, gangManager, rankManager,
		                           membershipService).getNode().getChildren().get(0).getData();
		demote = new GangDemoteCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), userManager,
		                               memberManager, gangManager, rankManager).getNode().getChildren().get(0)
		                                                                       .getData();
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("kick: the first member whose name matches ignoring case is kicked, the scan stops there")
	void kick_picksTheFirstCaseInsensitiveMatchAndStops() {
		Member ghost  = member(null, "ghost");
		Member empty  = member(null, "");
		Member bob    = member(null, "Bob");
		Member carl   = member(null, "Carl");
		Member carl2  = member(null, "carl");
		when(gang.getMembers()).thenReturn(List.of(ghost, empty, bob, carl, carl2));

		kick.executeArgument(actor, new String[]{"gang", "kick", "cARL"});

		verify(membershipService).kick(7, actor.getUniqueId(), carl.getUuid());
		verify(actorUser).sendMessage("KICK REFUSED");
		bukkit.statics().verify(() -> Bukkit.getOfflinePlayer(carl2.getUuid()), never());
		bukkit.statics().verify(() -> Bukkit.getOfflinePlayer(carl.getUuid()), times(1));
	}

	@Test
	@DisplayName("kick: no matching name (and an empty typed name never matching an empty member name) is 'not found'")
	void kick_withoutAMatchSaysPlayerNotFound() {
		Member ghost = member(null, null);
		Member empty = member(null, "");
		Member bob   = member(null, "Bob");
		when(gang.getMembers()).thenReturn(List.of(ghost, empty, bob));

		kick.executeArgument(actor, new String[]{"gang", "kick", "Zed"});
		kick.executeArgument(actor, new String[]{"gang", "kick", ""});

		verify(actorUser, times(2)).sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", "Zed"));
		verify(membershipService, never()).kick(any(Integer.class), any(UUID.class), any(UUID.class));
	}

	@Test
	@DisplayName("demote: the first member whose name matches ignoring case is demoted, the scan stops there")
	void demote_picksTheFirstCaseInsensitiveMatchAndStops() {
		Member ghost = member(highRank, null);
		Member empty = member(highRank, "");
		Member carl  = member(highRank, "Carl");
		Member carl2 = member(highRank, "CARL");
		when(gang.getMembers()).thenReturn(List.of(ghost, empty, carl, carl2));

		demote.executeArgument(actor, new String[]{"gang", "demote", "cArL"});

		verify(memberManager).assignRank(carl, lowRank);
		verify(memberManager, never()).assignRank(carl2, lowRank);
		bukkit.statics().verify(() -> Bukkit.getOfflinePlayer(carl2.getUuid()), never());
	}

	@Test
	@DisplayName("demote: no matching name (and an empty typed name never matching an empty member name) is 'not found'")
	void demote_withoutAMatchSaysPlayerNotFound() {
		Member ghost = member(highRank, null);
		Member empty = member(highRank, "");
		Member bob   = member(highRank, "Bob");
		when(gang.getMembers()).thenReturn(List.of(ghost, empty, bob));

		demote.executeArgument(actor, new String[]{"gang", "demote", "Zed"});
		demote.executeArgument(actor, new String[]{"gang", "demote", ""});

		verify(actorUser, times(2)).sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", "Zed"));
		verify(memberManager, never()).assignRank(any(), any());
	}

	private Member member(Rank rank, String name) {
		Member member = new Member(UUID.randomUUID());
		member.setRank(rank);
		OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
		when(offlinePlayer.getName()).thenReturn(name);
		bukkit.statics().when(() -> Bukkit.getOfflinePlayer(member.getUuid())).thenReturn(offlinePlayer);
		return member;
	}

}
