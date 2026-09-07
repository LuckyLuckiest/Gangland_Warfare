package org.luckyraven.gangland.gang.rank;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.datastructure.Tree;

/**
 * Decides whether one gang member may set another member's rank.
 *
 * <p>{@code /glw option gang rank <player> <rank>} — the command the promote menu's clickable rank list runs — only
 * checked that the target did not already hold the caller's own rank. Anything else went through: a recruit could
 * hand themselves, or anyone else, the owner rank, and the tab-completer offered every rank in the tree. The
 * hierarchy check {@code GangPromoteCommand} performs was simply absent here.
 *
 * <p>The rank tree is head-rooted: the lowest rank is the root and the owner is the deepest leaf, so "A outranks B"
 * means A's node is a descendant of B's node.
 *
 * <p>Observation #8 (gangs-ranks-mail.md), docket GR-08.
 */
public final class RankAssignmentPolicy {

	/**
	 * Why an assignment was refused, or that it may proceed.
	 */
	public enum Decision {
		/** The assignment may proceed. */
		ALLOWED,
		/** A member cannot change their own rank. */
		SELF,
		/** The caller holds no rank, so there is nothing to compare against. */
		NO_ACTOR_RANK,
		/** Caller and target hold the same rank. */
		SAME_RANK,
		/** The target's rank is equal to or above the caller's. */
		TARGET_OUTRANKS_ACTOR,
		/** The requested rank is not strictly below the caller's own rank. */
		RANK_NOT_BELOW_ACTOR
	}

	private RankAssignmentPolicy() {
	}

	/**
	 * @param tree the rank tree
	 * @param actorRank the caller's rank
	 * @param targetRank the target's current rank; {@code null} is treated as "below everything"
	 * @param requested the rank being assigned
	 * @param force the caller holds {@code gangland.command.gang.force_rank}, the same staff override
	 *              {@code GangPromoteCommand} honours
	 * @param self the caller and the target are the same member
	 *
	 * @return the decision; {@link Decision#ALLOWED} means the caller may assign {@code requested}
	 */
	public static Decision evaluate(@Nullable Tree<Rank> tree, @Nullable Rank actorRank, @Nullable Rank targetRank,
	                                @Nullable Rank requested, boolean force, boolean self) {
		// Self-action is a domain rule, never a permission decision — the same ordering GangPromoteCommand uses.
		if (self) return Decision.SELF;

		if (force) return Decision.ALLOWED;

		if (actorRank == null) return Decision.NO_ACTOR_RANK;
		if (tree == null || requested == null) return Decision.NO_ACTOR_RANK;

		if (actorRank.equals(targetRank)) return Decision.SAME_RANK;

		// The caller must strictly outrank the target: the caller's node sits below the target's in the tree.
		if (targetRank != null && !tree.isDescendant(targetRank.getNode(), actorRank.getNode())) {
			return Decision.TARGET_OUTRANKS_ACTOR;
		}

		// And may only hand out ranks strictly below their own — never their own rank, never one above it.
		if (!tree.isDescendant(requested.getNode(), actorRank.getNode())) {
			return Decision.RANK_NOT_BELOW_ACTOR;
		}

		return Decision.ALLOWED;
	}

	/**
	 * @return {@code true} when {@code actorRank} may be handed out by a caller holding {@code actorRank} — i.e.
	 *         {@code candidate} sits strictly below the caller in the tree. Used to narrow tab-completion to the
	 *         ranks the caller can actually assign.
	 */
	public static boolean assignable(@Nullable Tree<Rank> tree, @Nullable Rank actorRank, @Nullable Rank candidate) {
		if (tree == null || actorRank == null || candidate == null) return false;

		return tree.isDescendant(candidate.getNode(), actorRank.getNode());
	}

}
