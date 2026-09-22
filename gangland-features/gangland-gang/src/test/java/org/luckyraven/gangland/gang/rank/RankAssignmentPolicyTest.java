package org.luckyraven.gangland.gang.rank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.datastructure.Tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link RankAssignmentPolicy}, the hierarchy check now guarding
 * {@code /glw option gang rank <player> <rank>}.
 *
 * <p>Before docket GR-08 that command checked only "the target does not already hold my rank", so a recruit could
 * hand the owner rank to anyone — themselves included — and the tab-completer listed every rank in the tree. The
 * equivalent check in {@code GangPromoteCommand} was simply absent here.
 *
 * <p>Observation #8 (gangs-ranks-mail.md), docket GR-08.
 */
@DisplayName("RankAssignmentPolicy - who may hand out which rank")
class RankAssignmentPolicyTest {

	private Tree<Rank> tree;
	private Rank       recruit;
	private Rank       officer;
	private Rank       boss;

	/**
	 * Head-rooted tree: recruit (root) → officer → boss (leaf, the owner).
	 */
	@BeforeEach
	void buildTree() {
		recruit = new Rank("Recruit", 1);
		officer = new Rank("Officer", 2);
		boss    = new Rank("Boss", 3);

		recruit.getNode().add(officer.getNode());
		officer.getNode().add(boss.getNode());

		tree = new Tree<>();
		tree.add(recruit.getNode());
	}

	@Test
	@DisplayName("a recruit cannot hand out the owner rank - the GR-08 privilege escalation")
	void recruitCannotAssignOwnerRank() {
		assertEquals(RankAssignmentPolicy.Decision.TARGET_OUTRANKS_ACTOR,
		             RankAssignmentPolicy.evaluate(tree, recruit, officer, boss, false, false));
	}

	@Test
	@DisplayName("a recruit cannot promote a rankless member straight to owner either")
	void recruitCannotAssignRankAboveOwnEvenToARanklessTarget() {
		assertEquals(RankAssignmentPolicy.Decision.RANK_NOT_BELOW_ACTOR,
		             RankAssignmentPolicy.evaluate(tree, recruit, null, boss, false, false));
	}

	@Test
	@DisplayName("nobody may set their own rank, not even with force_rank")
	void selfAssignmentIsAlwaysRefused() {
		assertEquals(RankAssignmentPolicy.Decision.SELF,
		             RankAssignmentPolicy.evaluate(tree, boss, boss, boss, false, true));
		assertEquals(RankAssignmentPolicy.Decision.SELF,
		             RankAssignmentPolicy.evaluate(tree, boss, boss, boss, true, true));
	}

	@Test
	@DisplayName("equal ranks cannot act on each other")
	void sameRankIsRefused() {
		assertEquals(RankAssignmentPolicy.Decision.SAME_RANK,
		             RankAssignmentPolicy.evaluate(tree, officer, officer, recruit, false, false));
	}

	@Test
	@DisplayName("the boss may promote a recruit to officer - a rank strictly below their own")
	void leaderMayAssignBelowTheirOwnRank() {
		assertEquals(RankAssignmentPolicy.Decision.ALLOWED,
		             RankAssignmentPolicy.evaluate(tree, boss, recruit, officer, false, false));
	}

	@Test
	@DisplayName("nobody may hand out their own rank - that is a transfer, not a promotion")
	void assigningOwnRankIsRefused() {
		assertEquals(RankAssignmentPolicy.Decision.RANK_NOT_BELOW_ACTOR,
		             RankAssignmentPolicy.evaluate(tree, boss, recruit, boss, false, false));
	}

	@Test
	@DisplayName("force_rank keeps the staff override GangPromoteCommand already honoured")
	void forceRankBypassesTheHierarchy() {
		assertEquals(RankAssignmentPolicy.Decision.ALLOWED,
		             RankAssignmentPolicy.evaluate(tree, recruit, officer, boss, true, false));
	}

	@Test
	void missingActorRankOrTreeIsRefused() {
		assertEquals(RankAssignmentPolicy.Decision.NO_ACTOR_RANK,
		             RankAssignmentPolicy.evaluate(tree, null, recruit, officer, false, false));
		assertEquals(RankAssignmentPolicy.Decision.NO_ACTOR_RANK,
		             RankAssignmentPolicy.evaluate(null, boss, recruit, officer, false, false));
		assertEquals(RankAssignmentPolicy.Decision.NO_ACTOR_RANK,
		             RankAssignmentPolicy.evaluate(tree, boss, recruit, null, false, false));
	}

	@Test
	@DisplayName("assignable() narrows tab-completion to the ranks below the caller")
	void assignableMatchesTheEvaluateRule() {
		assertTrue(RankAssignmentPolicy.assignable(tree, boss, officer));
		assertTrue(RankAssignmentPolicy.assignable(tree, boss, recruit));

		assertFalse(RankAssignmentPolicy.assignable(tree, boss, boss));
		assertFalse(RankAssignmentPolicy.assignable(tree, recruit, boss));
		assertFalse(RankAssignmentPolicy.assignable(tree, null, boss));
		assertFalse(RankAssignmentPolicy.assignable(null, boss, recruit));
	}

}
