package org.luckyraven.gangland.gang.membership;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.GangMembershipService.Result;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The owner swap moved out of the transfer command: target takes the tail rank, owner drops one rank. */
@DisplayName("GangLeadership - transfer")
class GangLeadershipTest {

	private MockedStatic<Bukkit> bukkit;
	private MemberManager        memberManager;
	private GangLeadership       leadership;
	private Rank                 officer;
	private Rank                 owner;
	private Gang                 gang;
	private Member               ann;
	private Member               bob;
	private Player               bobPlayer;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		GangSettings.bind(new FakeGangSettingsContract());
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return path;
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
		bukkit = mockStatic(Bukkit.class);

		officer = new Rank("officer", 1);
		owner   = new Rank("owner", 2);
		officer.getNode().add(owner.getNode());

		RankManager rankManager = mock(RankManager.class);
		when(rankManager.get("owner")).thenReturn(owner);
		memberManager = mock(MemberManager.class);
		leadership    = new GangLeadership(memberManager, rankManager, mock(UserManager.class));

		gang = new Gang(1);
		ann  = member(owner);
		bob  = member(officer);

		bobPlayer = mock(Player.class);
		bukkit.when(() -> Bukkit.getPlayer(bob.getUuid())).thenReturn(bobPlayer);
		Player annPlayer = mock(Player.class);
		when(annPlayer.getName()).thenReturn("Ann");
		bukkit.when(() -> Bukkit.getPlayer(ann.getUuid())).thenReturn(annPlayer);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("transfer_matchesOldCommand")
	void transfer_matchesOldCommand() {
		Result result = leadership.transfer(gang, ann.getUuid(), bob.getUuid(), false);

		assertTrue(result.ok());
		verify(memberManager).assignRank(ann, officer);
		verify(memberManager).assignRank(bob, owner);
		verify(bobPlayer).sendMessage(Messages.GANG_TRANSFER_TARGET_SUCCESS.toString().replace("%player%", "Ann"));
	}

	@Test
	@DisplayName("transfer_refusesNonOwnerSelfAndStranger")
	void transfer_refusesNonOwnerSelfAndStranger() {
		assertEquals("Membership.Not_Owner", leadership.transfer(gang, bob.getUuid(), ann.getUuid(), false).messageKey());
		assertEquals("Membership.Self", leadership.transfer(gang, ann.getUuid(), ann.getUuid(), false).messageKey());
		assertEquals("Membership.Unknown_Player",
		             leadership.transfer(gang, ann.getUuid(), UUID.randomUUID(), false).messageKey());

		verify(memberManager, never()).assignRank(any(), any());
	}

	@Test
	@DisplayName("staff_bypassesOwnerCheckAndLock")
	void staff_bypassesOwnerCheckAndLock() {
		// staff acts without being the owner (or even a member): the current holder of the tail rank steps down
		Result result = leadership.transfer(gang, null, bob.getUuid(), true);

		assertTrue(result.ok());
		verify(memberManager).assignRank(ann, officer);
		verify(memberManager).assignRank(bob, owner);
		assertFalse(leadership.transfer(gang, bob.getUuid(), ann.getUuid(), false).ok(), "a player still needs to be owner");
	}

	private Member member(Rank rank) {
		Member member = new Member(UUID.randomUUID());
		gang.addMember(member, rank);
		when(memberManager.getMember(member.getUuid())).thenReturn(member);
		return member;
	}

}
