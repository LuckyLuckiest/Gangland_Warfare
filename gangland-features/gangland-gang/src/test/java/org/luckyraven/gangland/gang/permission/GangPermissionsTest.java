package org.luckyraven.gangland.gang.permission;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.permission.Permission;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.vault.permission.VaultPermissionBridge;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

	@Test
	@DisplayName("GR-03 / PLAN 15.4: ALL lists the gangland.gang.* nodes, gangland.turf.upgrade (W2-Q) and the "
	             + "rank-carried nodes of the gang parity wave (0.18.0)")
	void all_holdsTheRankNodes() {
		assertEquals(List.of(GangPermissions.WITHDRAW, GangPermissions.DEPOSIT, GangPermissions.RENAME,
		                     GangPermissions.DESCRIPTION, GangPermissions.DISPLAY, GangPermissions.COLOR,
		                     GangPermissions.INVITE, GangPermissions.ALLY, GangPermissions.TURF_UPGRADE,
		                     GangPermissions.KICK, GangPermissions.PROMOTE, GangPermissions.DEMOTE,
		                     GangPermissions.CHAT, GangPermissions.SAFEHOUSE, GangPermissions.SAFEHOUSE_MANAGE,
		                     GangPermissions.RALLY, GangPermissions.PVP, GangPermissions.ALLY_PVP,
		                     GangPermissions.UPGRADE, GangPermissions.FIGHT, GangPermissions.NOTICE,
		                     GangPermissions.RECRUITING),
		             GangPermissions.ALL);
	}

	@AfterEach
	void resetVault() {
		VaultPermissionBridge.set(null);
		GangPermissions.bind(Map::of, () -> null);
	}

	private static Tree<Rank> ladder(String... names) {
		Tree<Rank> tree = new Tree<>();
		Rank previous = null;
		for (int i = 0; i < names.length; i++) {
			Rank rank = new Rank(names[i], i + 1);
			if (previous == null) tree.add(rank.getNode());
			else previous.getNode().add(rank.getNode());
			previous = rank;
		}
		return tree;
	}

	private static Member holding(Tree<Rank> tree, String name) {
		return member(tree.getAllNodes().stream().map(Tree.Node::getData)
				              .filter(rank -> rank.getName().equals(name)).findFirst().orElseThrow());
	}

	private static void minimum(Tree<Rank> tree, String key, String rank) {
		GangPermissions.bind(() -> Map.of(key, rank), () -> tree);
	}

	@Test
	@DisplayName("PLAN 15.4: a rank exactly at the minimum passes (Tree.isDescendant is false for the same node)")
	void rankExactlyAtMinimum_passes() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Tree<Rank> tree = ladder("member", "officer", "owner");
			minimum(tree, "Withdraw", "officer");
			Member officer = holding(tree, "officer");

			assertFalse(tree.isDescendant(officer.getRank().getNode(), officer.getRank().getNode()));
			assertTrue(GangPermissions.allows(officer, null, GangPermissions.WITHDRAW));
		}
	}

	@Test
	@DisplayName("PLAN 15.4: a rank below the minimum is denied")
	void belowMinimum_denied() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Tree<Rank> tree = ladder("member", "officer", "owner");
			minimum(tree, "Withdraw", "officer");

			assertFalse(GangPermissions.allows(holding(tree, "member"), null, GangPermissions.WITHDRAW));
		}
	}

	@Test
	@DisplayName("PLAN 15.4: every rank above the minimum passes, on a 3- and a 5-rung tree")
	void aboveMinimum_passes() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Tree<Rank> five = ladder("member", "soldier", "officer", "underboss", "owner");
			minimum(five, "Invite", "soldier");
			assertFalse(GangPermissions.allows(holding(five, "member"), null, GangPermissions.INVITE));
			assertTrue(GangPermissions.allows(holding(five, "officer"), null, GangPermissions.INVITE));
			assertTrue(GangPermissions.allows(holding(five, "underboss"), null, GangPermissions.INVITE));

			Tree<Rank> three = ladder("member", "officer", "owner");
			minimum(three, "Invite", "member");
			assertTrue(GangPermissions.allows(holding(three, "member"), null, GangPermissions.INVITE));
			assertTrue(GangPermissions.allows(holding(three, "officer"), null, GangPermissions.INVITE));
		}
	}

	@Test
	@DisplayName("PLAN 15.4: on a branching tree a rank on another branch is not 'above' the minimum")
	void branchingTree() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rank member = new Rank("member", 1);
			Rank officer = new Rank("officer", 2);
			Rank thug = new Rank("thug", 3);
			Rank boss = new Rank("boss", 4);
			Tree<Rank> tree = new Tree<>();
			member.getNode().add(officer.getNode());
			member.getNode().add(thug.getNode());
			thug.getNode().add(new Rank("enforcer", 5).getNode());   // thug is not a leaf, so not the top rank
			officer.getNode().add(boss.getNode());
			tree.add(member.getNode());
			minimum(tree, "Kick", "officer");

			assertTrue(GangPermissions.allows(member(officer), null, GangPermissions.KICK));
			assertFalse(GangPermissions.allows(member(thug), null, GangPermissions.KICK));
			assertFalse(GangPermissions.allows(member(member), null, GangPermissions.KICK));
		}
	}

	@Test
	@DisplayName("PLAN 15.4: an unknown rank name warns once per name and turns that feature's clause off")
	void unknownRank_warnsOnceAndDisables() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Tree<Rank> tree = ladder("member", "officer", "owner");
			minimum(tree, "Withdraw", "ghost");
			Member officer = holding(tree, "officer");

			assertFalse(GangPermissions.allows(officer, null, GangPermissions.WITHDRAW));
			assertFalse(GangPermissions.allows(officer, null, GangPermissions.WITHDRAW));
			assertEquals(1, GangPermissions.unknownRankWarnings());
			assertNull(GangPermissions.lowestRankFor(GangPermissions.WITHDRAW));
		}
	}

	@Test
	@DisplayName("PLAN 15.4: the server permission and the top rank still pass; the clause only grants")
	void serverPermissionAndTopRank_stillPass() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Tree<Rank> tree = ladder("member", "officer", "owner");
			minimum(tree, "Withdraw", "owner");
			Player op = mock(Player.class);
			when(op.hasPermission(GangPermissions.WITHDRAW)).thenReturn(true);

			assertTrue(GangPermissions.allows(holding(tree, "member"), op, GangPermissions.WITHDRAW));
			assertTrue(GangPermissions.allows(holding(tree, "owner"), null, GangPermissions.WITHDRAW));
			assertFalse(GangPermissions.allows(holding(tree, "officer"), null, GangPermissions.WITHDRAW));
		}
	}

	@Test
	@DisplayName("a rank name matches case-insensitively, and the denial names the lowest rank that may act")
	void denial_namesTheLowestRank() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Tree<Rank> tree = ladder("member", "officer", "owner");
			minimum(tree, "Withdraw", "Officer");

			assertEquals("Officer", GangPermissions.lowestRankFor(GangPermissions.WITHDRAW));
			assertTrue(GangPermissions.allows(holding(tree, "officer"), null, GangPermissions.WITHDRAW));
			String line = org.bukkit.ChatColor.stripColor(GangPermissions.denial(GangPermissions.WITHDRAW));
			assertEquals("Withdraw needs Officer or higher.", line);
		}
	}

	@Test
	@DisplayName("GP8: a member refused /glw gang withdraw is told the lowest rank allowed, not the generic line")
	void refusal_namesTheMinimumRank() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Tree<Rank> tree = ladder("member", "officer", "owner");
			minimum(tree, "Withdraw", "officer");

			assertFalse(GangPermissions.allows(holding(tree, "member"), null, GangPermissions.WITHDRAW));
			String line = org.bukkit.ChatColor.stripColor(GangPermissions.refusal(GangPermissions.WITHDRAW));
			assertEquals("Withdraw needs officer or higher.", line);
		}
	}

	@Test
	@DisplayName("a node with no Ranks.Minimum keeps the generic no-permission line")
	void refusal_fallsBackWithoutMinimum() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Tree<Rank> tree = ladder("member", "officer", "owner");
			minimum(tree, "Withdraw", "officer");

			assertEquals(org.luckyraven.gangland.file.configuration.Messages.COMMAND_NO_PERM.toString(),
			             GangPermissions.refusal(GangPermissions.TURF_UPGRADE));
		}
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

	@Test
	@DisplayName("the force_rank staff override sits outside every namespace players are granted")
	void forceRank_isOutsidePlayerWildcards() {
		// Players get gangland.command.* to use /glw and ranks carry gangland.gang.* nodes: an override under either
		// wildcard let any member demote the owner.
		assertFalse(GangPermissions.FORCE_RANK.startsWith("gangland.command."));
		assertFalse(GangPermissions.FORCE_RANK.startsWith("gangland.gang."));
		assertTrue(GangPermissions.FORCE_RANK.endsWith("force_rank"));
	}

}
