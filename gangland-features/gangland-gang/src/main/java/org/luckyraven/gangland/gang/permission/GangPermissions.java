package org.luckyraven.gangland.gang.permission;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;

import java.util.List;

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

	/**
	 * Every {@code gangland.gang.*} node above. Rank permission add accepts exactly these (plus server nodes), and tab
	 * completion offers them.
	 */
	/** Buy guards and boosts at the Quartermaster (W2-Q). Not in ALL until W2-K commit 5. */
	public static final String TURF_UPGRADE = "gangland.turf.upgrade";

	/** Remove a member from the gang. */
	public static final String KICK = PREFIX + "kick";
	/** Promote a member. */
	public static final String PROMOTE = PREFIX + "promote";
	/** Demote a member. */
	public static final String DEMOTE = PREFIX + "demote";
	/** Use gang and ally chat. */
	public static final String CHAT = PREFIX + "chat";
	/** Teleport to a gang safehouse. */
	public static final String SAFEHOUSE = PREFIX + "safehouse";
	/** Set and remove gang safehouses. */
	public static final String SAFEHOUSE_MANAGE = PREFIX + "safehouse.manage";
	/** Call a rally. */
	public static final String RALLY = PREFIX + "rally";
	/** Switch friendly fire inside the gang. */
	public static final String PVP = PREFIX + "pvp";
	/** Switch friendly fire with an ally. */
	public static final String ALLY_PVP = PREFIX + "ally.pvp";
	/** Claim the next gang level. */
	public static final String UPGRADE = PREFIX + "upgrade";
	/** Start gang fights. */
	public static final String FIGHT = PREFIX + "fight";
	/** Set the gang notice. */
	public static final String NOTICE = PREFIX + "notice";
	/** Open and close recruiting. */
	public static final String RECRUITING = PREFIX + "recruiting";

	/**
	 * Server-permission nodes that ranks do not carry (not in {@link #ALL}): read another gang's balance, see the wealth
	 * ranking, read gang chat as staff, and run commands during a gang fight.
	 */
	public static final String BALANCE_OTHERS = PREFIX + "balance.others";
	public static final String RANKING_WEALTH = PREFIX + "ranking.wealth";
	public static final String SPY = "gangland.admin.gang.spy";
	public static final String FIGHT_BYPASS_COMMANDS = PREFIX + "fight.bypass_commands";

	public static final List<String> ALL = List.of(WITHDRAW, DEPOSIT, RENAME, DESCRIPTION, DISPLAY, COLOR, INVITE, ALLY,
	                                               TURF_UPGRADE, KICK, PROMOTE, DEMOTE, CHAT, SAFEHOUSE,
	                                               SAFEHOUSE_MANAGE, RALLY, PVP, ALLY_PVP, UPGRADE, FIGHT, NOTICE,
	                                               RECRUITING);

	/**
	 * Staff override that skips the rank-hierarchy checks of promote, demote and {@code option gang rank}. Kept out of
	 * {@code gangland.command.*} (granted to players so they can use /glw) and {@code gangland.gang.*} (carried by
	 * ranks), since either wildcard would hand it to every member.
	 */
	public static final String FORCE_RANK = "gangland.admin.gang.force_rank";

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
