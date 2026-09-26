package org.luckyraven.gangland.command.sub.gang;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luckyraven.gangland.GanglandApi;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Drives {@code /glw gang promote <player>} end to end through the real argument action.
 *
 * <p>Pins two defects from the test-server run: an owner's promote on the default {@code member -> owner} tree
 * handed out the owner (Tail) rank and minted a co-owner, and a rank with two or more children built the clickable
 * rank list but never sent it (docket GR-07).
 */
@DisplayName("GangPromoteCommand - who gets promoted to what")
class GangPromoteCommandTest {

	private BukkitStatics bukkit;
	private UserManager<Player> userManager;
	private MemberManager       memberManager;
	private RankManager         rankManager;
	private Player              actor;
	private Player.Spigot       spigot;
	private User<Player>        actorUser;
	private Member              actorMember;
	private Member              targetMember;
	private Tree<Rank>          rankTree;
	private Argument            promote;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		GangSettings.bind(new FakeGangSettingsContract());
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return null; // Messages.X.toString() then reads "<missing: path>", distinct per key
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
		bukkit = BukkitStatics.install();

		userManager   = mock(UserManager.class);
		memberManager = mock(MemberManager.class);
		rankManager   = mock(RankManager.class);
		GangManager gangManager = mock(GangManager.class);

		actor     = mock(Player.class);
		spigot    = mock(Player.Spigot.class);
		actorUser = mock(User.class);
		when(actor.getUniqueId()).thenReturn(UUID.randomUUID());
		when(actor.spigot()).thenReturn(spigot);
		when(userManager.getUser(actor)).thenReturn(actorUser);
		when(actorUser.hasGang()).thenReturn(true);
		when(actorUser.getGangId()).thenReturn(1);

		actorMember  = new Member(actor.getUniqueId());
		targetMember = new Member(UUID.randomUUID());
		when(memberManager.getMember(actor.getUniqueId())).thenReturn(actorMember);

		OfflinePlayer target = mock(OfflinePlayer.class);
		when(target.getName()).thenReturn("Carl");
		bukkit.statics().when(() -> Bukkit.getOfflinePlayer(targetMember.getUuid())).thenReturn(target);
		bukkit.statics().when(() -> Bukkit.getOfflinePlayer(actor.getUniqueId())).thenReturn(mock(OfflinePlayer.class));

		Gang gang = mock(Gang.class);
		when(gang.getMembers()).thenReturn(List.of(actorMember, targetMember));
		when(gangManager.getGang(1)).thenReturn(gang);

		rankTree = new Tree<>();
		when(rankManager.getRankTree()).thenReturn(rankTree);

		GangPromoteCommand command = new GangPromoteCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class),
		                                                    userManager, memberManager, gangManager, rankManager);
		promote = command.getNode().getChildren().get(0).getData();
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@ParameterizedTest(name = "force_rank = {0}")
	@ValueSource(booleans = {false, true})
	@DisplayName("an owner's promote never hands out the owner rank on the default member -> owner tree")
	void promoteIntoTailRank_isRefused(boolean force) {
		Rank member = rank("member", 1);
		Rank owner  = rank("owner", 2);
		member.getNode().add(owner.getNode());
		rankTree.add(member.getNode());

		actorMember.setRank(owner);
		targetMember.setRank(member);
		when(actor.hasPermission(GangPermissions.FORCE_RANK)).thenReturn(force);

		promote.executeArgument(actor, new String[]{"gang", "promote", "Carl"});

		verify(memberManager, never()).assignRank(any(), any());
		verify(actorUser).sendMessage(Messages.GANG_TRANSFER_OWNERSHIP.toString());
	}

	@Test
	@DisplayName("GR-07: a rank with two promotable children sends the clickable choice instead of doing nothing")
	void promoteWithTwoChildRanks_sendsTheChoice() {
		Rank member    = rank("member", 1);
		Rank enforcer  = rank("enforcer", 2);
		Rank dealer    = rank("dealer", 3);
		Rank owner     = rank("owner", 4);
		member.getNode().add(enforcer.getNode());
		member.getNode().add(dealer.getNode());
		enforcer.getNode().add(owner.getNode());
		rankTree.add(member.getNode());

		actorMember.setRank(owner);
		targetMember.setRank(member);
		when(actor.hasPermission(GangPermissions.FORCE_RANK)).thenReturn(true);

		promote.executeArgument(actor, new String[]{"gang", "promote", "Carl"});

		ArgumentCaptor<BaseComponent[]> sent = ArgumentCaptor.forClass(BaseComponent[].class);
		verify(spigot).sendMessage(sent.capture());
		Set<String> clicks = Arrays.stream(sent.getValue())
		                           .map(BaseComponent::getClickEvent)
		                           .filter(Objects::nonNull)
		                           .map(ClickEvent::getValue)
		                           .collect(Collectors.toSet());
		String command = "/" + GanglandApi.SHORT_PREFIX + " option gang rank Carl ";
		assertEquals(Set.of(command + "enforcer", command + "dealer"), clicks);
		verify(memberManager, never()).assignRank(any(), any());
	}

	private Rank rank(String name, int id) {
		Rank rank = new Rank(name, id);
		when(rankManager.get(name)).thenReturn(rank);
		return rank;
	}

}
