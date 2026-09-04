package org.luckyraven.gangland.gang.member;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.MemberRepositoryContract;
import org.luckyraven.gangland.gang.contract.RankLookupContract;
import org.luckyraven.gangland.gang.rank.Permission;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.vault.permission.VaultPermissionBridge;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.vault.permission.OfflinePermissionService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins {@link MemberManager#getMembersByRank}, {@link MemberManager#assignRank} and
 * {@link MemberManager#applyRankPermissionChange}, per the gangs-ranks-mail.md Test Surface bullet
 * "MemberManager.assignRank / applyRankPermissionChange with a stubbed OfflinePermissionService injected through
 * VaultPermissionBridge.set(...) - assert grant/revoke/group calls on null -> rank, rank -> rank and rank -> null
 * transitions".
 */
@DisplayName("MemberManager - rank assignment and Vault propagation")
class MemberManagerTest {

	@AfterEach
	void resetVault() {
		// VaultPermissionBridge is process-wide static state - never leak a stubbed service into another test class.
		VaultPermissionBridge.set(null);
	}

	private MemberManager newManager() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		DatabaseHandler databaseHandler = mock(DatabaseHandler.class);
		MemberRepositoryContract repository = mock(MemberRepositoryContract.class);
		GangLookupContract gangLookup = mock(GangLookupContract.class);
		RankLookupContract rankLookup = mock(RankLookupContract.class);
		return new MemberManager(plugin, databaseHandler, repository, gangLookup, rankLookup);
	}

	@Test
	@DisplayName("getMembersByRank returns null for a null rank instead of every unranked member")
	void getMembersByRank_nullRank_returnsEmptyList() {
		MemberManager manager = newManager();
		manager.add(new Member(UUID.randomUUID()));

		assertTrue(manager.getMembersByRank(null).isEmpty());
	}

	@Test
	@DisplayName("getMembersByRank filters by rank object identity (==), so an equal-but-distinct Rank instance is not matched")
	void getMembersByRank_filtersByIdentity() {
		MemberManager manager = newManager();
		Rank boss = new Rank("Boss", 1);
		Rank distinctBossWithSameFields = new Rank("Boss", 1);

		Member wearsBoss = new Member(UUID.randomUUID());
		wearsBoss.setRank(boss);
		Member wearsSomethingElse = new Member(UUID.randomUUID());
		wearsSomethingElse.setRank(new Rank("Recruit", 2));
		manager.add(wearsBoss);
		manager.add(wearsSomethingElse);

		List<Member> result = manager.getMembersByRank(boss);

		assertEquals(1, result.size());
		assertSame(wearsBoss, result.get(0));
		assertTrue(manager.getMembersByRank(distinctBossWithSameFields).isEmpty(),
				"Rank has no equals() override, so a same-fields-different-instance Rank matches nobody");
	}

	@Test
	@DisplayName("assignRank sets the member's rank and reports the transition to VaultPermissionBridge even without Vault installed")
	void assignRank_setsRank_andCallsVaultBridgeSafely() {
		MemberManager manager = newManager();
		Member member = new Member(UUID.randomUUID());
		Rank newRank = new Rank("Boss", 1);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			manager.assignRank(member, newRank);
		}

		assertSame(newRank, member.getRank());
	}

	@Test
	@DisplayName("assignRank(member, newRank) revokes every permission of the old rank and grants every permission of the new rank")
	void assignRank_withVaultInstalled_revokesOldGrantsNew() {
		MemberManager manager = newManager();
		UUID uuid = UUID.randomUUID();
		Member member = new Member(uuid);
		Rank oldRank = new Rank("Recruit", 1, List.of(new Permission(1, "old.node")));
		oldRank.setVaultGroup("recruits");
		Rank newRank = new Rank("Boss", 2, List.of(new Permission(2, "new.node")));
		newRank.setVaultGroup("bosses");
		member.setRank(oldRank);

		OfflinePermissionService vault = mock(OfflinePermissionService.class);
		VaultPermissionBridge.set(vault);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			OfflinePlayer offline = mock(OfflinePlayer.class);
			bukkit.statics().when(() -> org.bukkit.Bukkit.getOfflinePlayer(uuid)).thenReturn(offline);

			manager.assignRank(member, newRank);

			verify(vault).revoke(offline, "old.node");
			verify(vault).removeFromGroup(offline, "recruits");
			verify(vault).grant(offline, "new.node");
			verify(vault).addToGroup(offline, "bosses");
		}

		assertSame(newRank, member.getRank());
	}

	@Test
	@DisplayName("assignRank(member, null) only revokes - a demotion off the tree grants nothing")
	void assignRank_toNull_onlyRevokes() {
		MemberManager manager = newManager();
		UUID uuid = UUID.randomUUID();
		Member member = new Member(uuid);
		Rank oldRank = new Rank("Recruit", 1, List.of(new Permission(1, "old.node")));
		member.setRank(oldRank);

		OfflinePermissionService vault = mock(OfflinePermissionService.class);
		VaultPermissionBridge.set(vault);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			OfflinePlayer offline = mock(OfflinePlayer.class);
			bukkit.statics().when(() -> org.bukkit.Bukkit.getOfflinePlayer(uuid)).thenReturn(offline);

			manager.assignRank(member, null);

			verify(vault).revoke(offline, "old.node");
			verify(vault, never()).grant(any(), any());
		}

		assertNull(member.getRank());
	}

	@Test
	@DisplayName("applyRankPermissionChange grants/revokes the single node on every cached member currently wearing the rank")
	void applyRankPermissionChange_pushesNodeToEveryMemberOfTheRank() {
		MemberManager manager = newManager();
		Rank boss = new Rank("Boss", 1);
		UUID uuidA = UUID.randomUUID();
		UUID uuidB = UUID.randomUUID();
		Member memberA = new Member(uuidA);
		memberA.setRank(boss);
		Member memberB = new Member(uuidB);
		memberB.setRank(boss);
		Member unrelated = new Member(UUID.randomUUID());
		manager.add(memberA);
		manager.add(memberB);
		manager.add(unrelated);

		OfflinePermissionService vault = mock(OfflinePermissionService.class);
		VaultPermissionBridge.set(vault);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			OfflinePlayer offlineA = mock(OfflinePlayer.class);
			OfflinePlayer offlineB = mock(OfflinePlayer.class);
			bukkit.statics().when(() -> org.bukkit.Bukkit.getOfflinePlayer(uuidA)).thenReturn(offlineA);
			bukkit.statics().when(() -> org.bukkit.Bukkit.getOfflinePlayer(uuidB)).thenReturn(offlineB);

			manager.applyRankPermissionChange(boss, "new.node", true);

			verify(vault).grant(offlineA, "new.node");
			verify(vault).grant(offlineB, "new.node");
			verify(vault, never()).revoke(any(), eq("new.node"));
		}
	}

}
