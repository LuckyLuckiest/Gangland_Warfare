package org.luckyraven.gangland.gang;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.vault.permission.VaultPermissionBridge;
import org.luckyraven.gangland.inventory.service.InventoryRegistry;
import org.luckyraven.keystone.util.Placeholder;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link Gang#addMember} / {@link Gang#removeMember} as the only mutators that keep the three-sided
 * membership link ({@code User.gangId}, {@code Member.gangId}/{@code Member.rank}, {@code Gang.members}) in step,
 * per the gangs-ranks-mail.md overview ("Membership is a *three-sided* link ... only Gang.addMember /
 * Gang.removeMember keep all three in step").
 */
@DisplayName("Gang - addMember/removeMember three-way invariants")
class GangMembershipTest {

	@BeforeEach
	void bindSettings() {
		GangSettings.bind(new FakeGangSettingsContract());
	}

	@AfterEach
	void resetVault() {
		// VaultPermissionBridge is process-wide static state; make sure no test in this class leaves a stub installed.
		VaultPermissionBridge.set(null);
	}

	@Test
	@DisplayName("addMember(Member, Rank) sets gangId/rank on the member and appends it to Gang.members exactly once")
	void addMember_memberOnly_setsRankAndAppends() {
		Gang   gang   = new Gang(1);
		Member member = new Member(UUID.randomUUID());
		Rank   rank   = new Rank("Recruit", 1);

		gang.addMember(member, rank);

		assertEquals(1, gang.getId(), "sanity: constructor id preserved");
		assertEquals(gang.getId(), member.getGangId());
		assertSame(rank, member.getRank());
		assertTrue(gang.getMembers().contains(member));
		assertEquals(1, gang.getMembers().size());
	}

	@Test
	@DisplayName("addMember is idempotent: re-adding an already-present member does not duplicate the list entry")
	void addMember_isIdempotentForPresentMember() {
		Gang   gang   = new Gang(2);
		Member member = new Member(UUID.randomUUID());
		Rank   rank   = new Rank("Recruit", 2);

		gang.addMember(member, rank);
		gang.addMember(member, rank);

		assertEquals(1, gang.getMembers().size(), "the member list must not gain a duplicate entry");
	}

	@Test
	@DisplayName("addMember(User, Member, Rank) also sets User.gangId, so all three sides agree")
	void addMember_withUser_setsUserGangIdToo() {
		Gang         gang   = new Gang(3);
		Member       member = new Member(UUID.randomUUID());
		Rank         rank   = new Rank("Recruit", 3);
		User<Player> user   = newOnlineUser();

		gang.addMember(user, member, rank);

		assertEquals(gang.getId(), user.getGangId());
		assertEquals(gang.getId(), member.getGangId());
		assertSame(rank, member.getRank());
		assertTrue(gang.getMembers().contains(member));
	}

	@Test
	@DisplayName("removeMember(Member) resets gangId to -1, clears contribution and rank, and drops the member from the list")
	void removeMember_memberOnly_resetsAndDrops() {
		Gang   gang   = new Gang(4);
		Member member = new Member(UUID.randomUUID());
		Rank   rank   = new Rank("Recruit", 4);
		gang.addMember(member, rank);
		member.setContribution(500D);

		gang.removeMember(member);

		assertEquals(-1, member.getGangId());
		assertEquals(0D, member.getContribution());
		assertNull(member.getRank());
		assertFalse(gang.getMembers().contains(member));
	}

	@Test
	@DisplayName("removeMember(User, Member) flushes the user's permissions, resets User.gangId, and removes the member")
	void removeMember_withUser_resetsAllThreeSides() {
		Gang         gang   = new Gang(5);
		Member       member = new Member(UUID.randomUUID());
		Rank         rank   = new Rank("Recruit", 5);
		User<Player> user   = newOnlineUser();
		gang.addMember(user, member, rank);

		gang.removeMember(user, member);

		assertFalse(user.hasGang(), "User.resetGang() must run");
		assertEquals(-1, member.getGangId());
		assertNull(member.getRank());
		assertFalse(gang.getMembers().contains(member));
	}

	@Test
	@DisplayName("removeMember(Member) is a no-op when the member is not actually in the list (guarded by contains())")
	void removeMember_noop_whenMemberNotPresent() {
		Gang   gang    = new Gang(6);
		Member member  = new Member(UUID.randomUUID());
		Rank   rank    = new Rank("Recruit", 6);
		member.setRank(rank); // pretend it already carries a rank from elsewhere

		gang.removeMember(member);

		assertSame(rank, member.getRank(), "the guard returns before touching a member that was never added");
	}

	// --- helpers -------------------------------------------------------------------------------

	private User<Player> newOnlineUser() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		Player     player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());

		Placeholder        placeholder = mock(Placeholder.class);
		InventoryRegistry  registry    = new InventoryRegistry();

		User<Player> user = new User<>(plugin, player, placeholder, registry);

		PermissionAttachment attachment = mock(PermissionAttachment.class);
		when(attachment.getPermissions()).thenReturn(Collections.emptyMap());
		user.setPermissionAttachment(attachment);

		return user;
	}

}
