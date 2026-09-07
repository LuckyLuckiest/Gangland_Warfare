package org.luckyraven.gangland.gang.permission;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Permission;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.vault.permission.VaultPermissionBridge;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link GangPermissions}, the gate now guarding every mutating gang subcommand.
 *
 * <p>Before docket GR-03 there was no gate at all: {@code /glw gang withdraw} let the lowest-ranked member drain
 * the vault, and {@code deposit}, {@code rename}, {@code desc}, {@code display}, {@code color}, {@code invite} and
 * the {@code ally} family were equally open. {@code Member.hasPermission} and the whole rank-permission list had no
 * production caller.
 *
 * <p>Observation #3 (gangs-ranks-mail.md), docket GR-03.
 */
@DisplayName("GangPermissions - rank gate on the mutating gang subcommands")
class GangPermissionsTest {

	@AfterEach
	void resetVault() {
		VaultPermissionBridge.set(null);
	}

	/**
	 * Head-rooted tree: recruit (root) → officer → boss (leaf, the gang leader).
	 */
	private static Rank[] rankTree() {
		Rank recruit = new Rank("Recruit", 1);
		Rank officer = new Rank("Officer", 2);
		Rank boss    = new Rank("Boss", 3);

		Tree<Rank> tree = new Tree<>();
		recruit.getNode().add(officer.getNode());
		officer.getNode().add(boss.getNode());
		tree.add(recruit.getNode());

		return new Rank[]{recruit, officer, boss};
	}

	private static Member member(Rank rank) {
		Member member = new Member(UUID.randomUUID());
		member.setGangId(1);
		member.setRank(rank);
		return member;
	}

	@Test
	@DisplayName("a rank without the node is refused - this is the ungated vault the docket reported")
	void allows_deniesRankWithoutTheNode() {
		Rank[] ranks   = rankTree();
		Member recruit = member(ranks[0]);

		Player player = mock(Player.class);
		when(player.hasPermission(GangPermissions.WITHDRAW)).thenReturn(false);

		// Member.hasPermission falls back to Bukkit.getOfflinePlayer for the Vault half of the check.
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertFalse(GangPermissions.allows(recruit, player, GangPermissions.WITHDRAW));
		}
	}

	@Test
	@DisplayName("a rank carrying the node is allowed")
	void allows_acceptsRankWithTheNode() {
		Rank   officer = new Rank("Officer", 2, List.of(new Permission(1, GangPermissions.WITHDRAW)));
		Rank   boss    = new Rank("Boss", 3);
		officer.getNode().add(boss.getNode());

		Player player = mock(Player.class);
		when(player.hasPermission(GangPermissions.WITHDRAW)).thenReturn(false);

		assertTrue(GangPermissions.allows(member(officer), player, GangPermissions.WITHDRAW));
	}

	@Test
	@DisplayName("the gang leader keeps every power - upgrading servers have no nodes configured yet")
	void allows_topRankAlwaysPasses() {
		Rank[] ranks = rankTree();
		Member boss  = member(ranks[2]);

		Player player = mock(Player.class);
		when(player.hasPermission(GangPermissions.RENAME)).thenReturn(false);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertTrue(GangPermissions.allows(boss, player, GangPermissions.RENAME));
		}

		assertTrue(GangPermissions.isTopRank(boss));
		assertFalse(GangPermissions.isTopRank(member(ranks[0])));
	}

	@Test
	@DisplayName("a server permission grants the node without any gang rank at all")
	void allows_serverPermissionIsTheAdminPath() {
		Player player = mock(Player.class);
		when(player.hasPermission(GangPermissions.ALLY)).thenReturn(true);

		assertTrue(GangPermissions.allows(null, player, GangPermissions.ALLY));
	}

	@Test
	void allows_deniesWithoutMemberOrNode() {
		Player player = mock(Player.class);

		assertFalse(GangPermissions.allows(null, player, GangPermissions.INVITE));
		assertFalse(GangPermissions.allows(member(new Rank("Recruit", 1)), player, null));
		assertFalse(GangPermissions.allows(member(new Rank("Recruit", 1)), player, ""));
	}

	@Test
	@DisplayName("a member with no rank at all is refused")
	void allows_deniesRanklessMember() {
		Member rankless = new Member(UUID.randomUUID());
		rankless.setGangId(1);

		Player player = mock(Player.class);
		when(player.hasPermission(GangPermissions.DEPOSIT)).thenReturn(false);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertFalse(GangPermissions.allows(rankless, player, GangPermissions.DEPOSIT));
		}

		assertFalse(GangPermissions.isTopRank(rankless));
		assertFalse(GangPermissions.isTopRank(null));
	}

	@Test
	@DisplayName("every guarded subcommand has its own node")
	void nodes_areDistinctAndNamespaced() {
		List<String> nodes = List.of(GangPermissions.WITHDRAW, GangPermissions.DEPOSIT, GangPermissions.RENAME,
		                             GangPermissions.DESCRIPTION, GangPermissions.DISPLAY, GangPermissions.COLOR,
		                             GangPermissions.INVITE, GangPermissions.ALLY);

		assertTrue(nodes.stream().allMatch(node -> node.startsWith("gangland.gang.")));
		assertTrue(nodes.stream().distinct().count() == nodes.size(), "no two subcommands share a node");
	}

}
