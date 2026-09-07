package org.luckyraven.gangland.gang.permission;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;

/**
 * Rank-permission nodes for the gang subcommands that change gang state, and the single gate that decides them.
 *
 * <p>{@code Member.hasPermission} and the whole {@code Rank} permission list existed with no production caller: every
 * mutating gang subcommand — {@code withdraw}, {@code deposit}, {@code rename}, {@code desc}, {@code display},
 * {@code color}, {@code invite} and the {@code ally} family — ran for any member of the gang, so the newest recruit
 * could drain the vault or rename the gang.
 *
 * <p>The gate answers yes when any of these hold:
 * <ol>
 *   <li>the server grants the node to the player directly (op, or a permission plugin) — this is the admin path;</li>
 *   <li>the member's rank carries the node, or Vault grants it (that is {@link Member#hasPermission(String)});</li>
 *   <li>the member holds the gang's top rank. The rank tree is head-rooted, so the top rank is the leaf with no
 *       children. Without this clause every gang on an upgrading server would freeze the moment this shipped: no
 *       existing rank has any of these nodes configured yet.</li>
 * </ol>
 *
 * <p>Observation #3 (gangs-ranks-mail.md), docket GR-03.
 */
public final class GangPermissions {

	private static final String PREFIX = "gangland.gang.";

	/** Take money out of the gang vault. */
	public static final String WITHDRAW = PREFIX + "withdraw";
	/** Put money into the gang vault. */
	public static final String DEPOSIT = PREFIX + "deposit";
	/** Change the gang name. */
	public static final String RENAME = PREFIX + "rename";
	/** Change the gang description. */
	public static final String DESCRIPTION = PREFIX + "description";
	/** Change the gang display name. */
	public static final String DISPLAY = PREFIX + "display";
	/** Change the gang colour. */
	public static final String COLOR = PREFIX + "color";
	/** Invite a player, and cancel a pending invite. */
	public static final String INVITE = PREFIX + "invite";
	/** Request, accept, reject or abandon an alliance. */
	public static final String ALLY = PREFIX + "ally";

	private GangPermissions() {
	}

	/**
	 * Decides whether {@code player} may run a gang subcommand guarded by {@code node}.
	 *
	 * @param member the caller's cached member row; {@code null} allows only the server-permission path
	 * @param player the caller; {@code null} falls back to the member's own rank/Vault permissions
	 * @param node one of the constants on this class
	 *
	 * @return {@code true} when the command may proceed
	 */
	public static boolean allows(@Nullable Member member, @Nullable Player player, String node) {
		if (node == null || node.isEmpty()) return false;

		if (player != null && player.hasPermission(node)) return true;

		if (member == null) return false;

		if (member.hasPermission(node)) return true;

		return isTopRank(member);
	}

	/**
	 * @return {@code true} when the member holds the deepest rank in the tree — the gang leader
	 */
	public static boolean isTopRank(@Nullable Member member) {
		if (member == null) return false;

		Rank rank = member.getRank();

		if (rank == null) return false;

		return rank.getNode().getChildren().isEmpty();
	}

}
