package org.luckyraven.gangland.gang.member;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.rank.Permission;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.vault.permission.VaultPermissionBridge;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.vault.permission.OfflinePermissionService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link Member#hasPermission(String)}: rank-node check first, Vault fallback second, per the Component table
 * ("Member.hasPermission = rank node OR Vault") in gangs-ranks-mail.md.
 */
@DisplayName("Member - hasPermission combined rank/Vault check")
class MemberTest {

	@AfterEach
	void resetVault() {
		VaultPermissionBridge.set(null);
	}

	@Test
	void hasPermission_falseForNullOrEmptyNode() {
		Member member = new Member(UUID.randomUUID());

		assertFalse(member.hasPermission(null));
		assertFalse(member.hasPermission(""));
	}

	@Test
	@DisplayName("a rank node match short-circuits before touching Bukkit/Vault at all")
	void hasPermission_trueWhenRankContainsNode_caseInsensitive() {
		Member member = new Member(UUID.randomUUID());
		Rank   rank   = new Rank("Boss", 1, List.of(new Permission(1, "gangland.command.gang.force_rank")));
		member.setRank(rank);

		assertTrue(member.hasPermission("GANGLAND.COMMAND.GANG.FORCE_RANK"));
	}

	@Test
	@DisplayName("no rank and no Vault service installed -> false (VaultPermissionBridge.has is a safe no-op)")
	void hasPermission_falseWithoutRankOrVault() {
		Member member = new Member(UUID.randomUUID());

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertFalse(member.hasPermission("some.node"));
		}
	}

	@Test
	@DisplayName("rank does not carry the node, but Vault reports it -> true")
	void hasPermission_fallsBackToVault_whenRankMisses() {
		Member member = new Member(UUID.randomUUID());
		Rank   rank   = new Rank("Recruit", 2);
		member.setRank(rank);

		OfflinePermissionService vault = mock(OfflinePermissionService.class);
		when(vault.has(org.mockito.ArgumentMatchers.any(OfflinePlayer.class), org.mockito.ArgumentMatchers.eq("vault.only.node")))
				.thenReturn(true);
		VaultPermissionBridge.set(vault);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			OfflinePlayer offline = mock(OfflinePlayer.class);
			bukkit.statics().when(() -> org.bukkit.Bukkit.getOfflinePlayer(member.getUuid())).thenReturn(offline);

			assertTrue(member.hasPermission("vault.only.node"));
		}
	}

}
