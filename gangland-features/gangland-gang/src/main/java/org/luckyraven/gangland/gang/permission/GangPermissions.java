package org.luckyraven.gangland.gang.permission;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;

import lombok.CustomLog;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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
 *   <li>{@code Ranks.Minimum} in gang_rules.yml names a rank for the feature and the member's rank is that rank or any
 *       rank below it toward the Tail. This clause only grants; it never removes a node a rank already holds.</li>
 * </ol>
 *
 * <p>Observation #3 (gangs-ranks-mail.md), docket GR-03.
 */
@CustomLog
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

	/** Ranks.Minimum key of each rank-carried node; a node missing here has no minimum (turf upgrade). */
	private static final Map<String, String> FEATURES = Map.ofEntries(
			Map.entry(WITHDRAW, "Withdraw"), Map.entry(DEPOSIT, "Deposit"), Map.entry(RENAME, "Rename"),
			Map.entry(DESCRIPTION, "Description"), Map.entry(DISPLAY, "Display"), Map.entry(COLOR, "Color"),
			Map.entry(INVITE, "Invite"), Map.entry(ALLY, "Ally"), Map.entry(KICK, "Kick"),
			Map.entry(PROMOTE, "Promote"), Map.entry(DEMOTE, "Demote"), Map.entry(CHAT, "Chat"),
			Map.entry(SAFEHOUSE, "Safehouse"), Map.entry(SAFEHOUSE_MANAGE, "Safehouse_Manage"),
			Map.entry(RALLY, "Rally"), Map.entry(PVP, "Friendly_Fire"), Map.entry(ALLY_PVP, "Friendly_Fire"),
			Map.entry(UPGRADE, "Upgrade"), Map.entry(FIGHT, "Fight"), Map.entry(NOTICE, "Notice"),
			Map.entry(RECRUITING, "Recruiting"));

	private static final String DENIAL_FALLBACK = "&c%feature% needs &f%rank% &cor higher.";

	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	private static volatile Supplier<Map<String, String>> minimums = Map::of;
	private static volatile Supplier<Tree<Rank>>          rankTree = () -> null;
	private static volatile GangMessages                  messages;

	private GangPermissions() {
	}

	/**
	 * Binds the Ranks.Minimum map and the rank tree once at startup; the suppliers are read on every check, so a
	 * reload of gang_rules.yml is picked up with no second bind (like {@code GangLevels.bind}).
	 */
	public static void bind(Supplier<Map<String, String>> minimumRanks, Supplier<Tree<Rank>> tree) {
		minimums = minimumRanks;
		rankTree = tree;
		WARNED.clear();
	}

	/** Binds the text source of {@link #denial(String)}. */
	public static void bindMessages(GangMessages gangMessages) {
		messages = gangMessages;
	}

	/** Number of distinct unknown rank names warned about since the last bind. */
	static int unknownRankWarnings() {
		return WARNED.size();
	}

	/**
	 * @return the Ranks.Minimum rank name for {@code node} as written in the file, or {@code null} when the node has no
	 * minimum or names a rank that is not in the tree
	 */
	public static @Nullable String lowestRankFor(String node) {
		String feature = FEATURES.get(node);
		if (feature == null) return null;

		String name = minimums.get().get(feature);
		if (name == null || name.isBlank()) return null;

		return minimumNode(name) == null ? null : name;
	}

	/**
	 * The refusal line for {@code node}: "Withdraw needs officer or higher." (gang_messages.yml {@code Ranks.Needs_Rank}),
	 * or {@code null} when the node has no usable minimum, so the caller keeps its own message.
	 */
	public static @Nullable String denial(String node) {
		String rank = lowestRankFor(node);
		if (rank == null) return null;

		String feature = FEATURES.get(node);
		if (messages != null) return messages.text("Ranks.Needs_Rank", DENIAL_FALLBACK, "feature", feature, "rank", rank);

		return GanglandChatUtil.color(DENIAL_FALLBACK.replace("%feature%", feature).replace("%rank%", rank));
	}

	private static @Nullable Tree.Node<Rank> minimumNode(String name) {
		Tree<Rank> tree = rankTree.get();
		if (tree == null) return null;

		for (Tree.Node<Rank> candidate : tree.getAllNodes()) {
			if (candidate.getData().getName().equalsIgnoreCase(name.trim())) return candidate;
		}

		if (WARNED.add(name.trim().toLowerCase(Locale.ROOT))) {
			log.warn("gang_rules.yml Ranks.Minimum names the rank '{}', which does not exist; that entry is ignored", name);
		}
		return null;
	}

	private static boolean meetsMinimum(Member member, String node) {
		String feature = FEATURES.get(node);
		if (feature == null || member.getRank() == null) return false;

		String name = minimums.get().get(feature);
		if (name == null || name.isBlank()) return false;

		Tree.Node<Rank> minimum = minimumNode(name);
		if (minimum == null) return false;

		Tree.Node<Rank> own = member.getRank().getNode();
		// Tree.isDescendant is false for the same node, so "exactly at the minimum" needs the equality test.
		return own == minimum || rankTree.get().isDescendant(minimum, own);
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

		return isTopRank(member) || meetsMinimum(member, node);
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
