package org.luckyraven.gangland.command.sub.gang;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.GanglandApi;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.permission.RankPermissionApplier;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankAssignmentPolicy;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.luckyraven.keystone.bean.Qualifier;

class GangPromoteCommand extends SubArgument {

	private final JavaPlugin            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;

	protected GangPromoteCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                             @Qualifier("online") UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                             RankManager rankManager) {
		super(gangland, "promote", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.rankManager   = rankManager;

		this.addSubArgument(gangPromote());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private OptionalArgument gangPromote() {
		return new OptionalArgument(gangland, tree, this::promote, this::promotableMembers);
	}

	private void promote(Argument argument, CommandSender sender, String[] args) {
		Player       player = (Player) sender;
		User<Player> user   = userManager.getUser(player);

		if (user == null) return;

		Member userMember = memberManager.getMember(player.getUniqueId());

		boolean force = player.hasPermission(GangPermissions.FORCE_RANK);

		// GR-01: a player with no cached Member would NPE further down this command.
		if (userMember == null || !user.hasGang()) {
			user.sendMessage(Messages.MUST_CREATE_GANG.toString());
			return;
		}

		// PLAN 15.4: the rank gate (node, Ranks.Minimum or leader) comes before the rank-position checks below.
		if (!force && !GangPermissions.allows(userMember, player, GangPermissions.PROMOTE)) {
			String denial = GangPermissions.denial(GangPermissions.PROMOTE);
			user.sendMessage(denial != null ? denial : Messages.COMMAND_NO_PERM.toString());
			return;
		}

		Gang gang = gangManager.getGang(user.getGangId());

		String targetStr    = args[2];
		Member targetMember = findMember(gang, targetStr);

		if (targetMember == null) {
			user.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
			return;
		}

		// Self-action is a domain rule, never a permission decision — applied before the force/rank gate.
		if (targetMember.getUuid().equals(player.getUniqueId())) {
			user.sendMessage(Messages.GANG_CANNOT_ACT_SELF.toString());
			return;
		}

		if (targetMember.getRank() == null) {
			user.sendMessage(Messages.INVALID_RANK.toString());
			return;
		}

		// change the user rank by proceeding to the next node
		Tree<Rank> rankTree    = rankManager.getRankTree();
		Rank       currentRank = Objects.requireNonNull(rankTree.find(targetMember.getRank()));
		Rank       tail        = rankManager.get(GangSettings.getGangRankTail());

		// Only the next ranks this caller may hand out: strictly below their own, never the owner (Tail) rank.
		List<Rank>                    nextRanks = new ArrayList<>();
		RankAssignmentPolicy.Decision refusal   = null;
		for (Tree.Node<Rank> child : currentRank.getNode().getChildren()) {
			RankAssignmentPolicy.Decision decision = RankAssignmentPolicy.evaluate(
					rankTree, userMember.getRank(), currentRank, child.getData(), force, false, tail);

			if (decision == RankAssignmentPolicy.Decision.ALLOWED) nextRanks.add(child.getData());
			else if (refusal == null) refusal = decision;
		}

		if (nextRanks.isEmpty()) {
			user.sendMessage(refusal == null ? Messages.GANG_PROMOTE_END.toString()
			                                 : RankAssignmentPolicy.message(refusal));
			return;
		}

		if (nextRanks.size() > 1) {
			sendRankChoice(player, targetStr, nextRanks);
			return;
		}

		promoteTo(user, targetMember, targetStr, nextRanks.get(0));
	}

	private static Member findMember(Gang gang, String name) {
		return gang.getMembers().stream().filter(member -> hasName(member, name)).findFirst().orElse(null);
	}

	private static boolean hasName(Member member, String name) {
		String offlineName = Bukkit.getOfflinePlayer(member.getUuid()).getName();

		return offlineName != null && !offlineName.isEmpty() && offlineName.equalsIgnoreCase(name);
	}

	private static void sendRankChoice(Player player, String targetStr, List<Rank> nextRanks) {
		ComponentBuilder ranks = new ComponentBuilder();

		for (int i = 0; i < nextRanks.size(); i++) {
			String rank = nextRanks.get(i).getName();

			var value = String.format("/%s option gang rank %s %s", GanglandApi.SHORT_PREFIX, targetStr, rank);
			var sep   = new ComponentBuilder(rank).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, value));

			ranks.append(sep.create());

			if (i < nextRanks.size() - 1) ranks.append("  ");
		}

		player.spigot().sendMessage(ranks.create());
	}

	private void promoteTo(User<Player> user, Member targetMember, String targetStr, Rank rank) {
		notifyPromotedTarget(targetMember, rank);

		user.sendMessage(Messages.GANG_PROMOTE_PLAYER_SUCCESS.toString()
		                                                     .replace("%player%", targetStr)
		                                                     .replace("%rank%", rank.getName()));

		memberManager.assignRank(targetMember, rank);
	}

	private void notifyPromotedTarget(Member targetMember, Rank rank) {
		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());
		String        offlineName   = offlinePlayer.getName();

		if (offlineName == null || offlineName.isEmpty() || !offlinePlayer.isOnline()) return;

		Player onlinePlayer = offlinePlayer.getPlayer();
		String message      = Messages.GANG_PROMOTE_TARGET_SUCCESS.toString().replace("%rank%", rank.getName());
		// remove the previous rank attachments
		User<Player> onlineUser = userManager.getUser(onlinePlayer);

		if (onlineUser != null) {
			RankPermissionApplier.flush(onlineUser, rank);
		}

		Objects.requireNonNull(onlinePlayer).sendMessage(message);
	}

	private List<String> promotableMembers(CommandSender sender) {
		Player       player = (Player) sender;
		User<Player> user   = userManager.getUser(player);

		if (user == null) return null;

		Member userMember = memberManager.getMember(player.getUniqueId());

		// GR-01: a player with no cached Member would NPE further down this command.
		if (userMember == null || !user.hasGang()) {
			return null;
		}

		Gang userGang = gangManager.getGang(user.getGangId());
		Rank userRank = userMember.getRank();

		if (userRank == null) {
			return null;
		}

		// the names of the members this user outranks
		List<String> descendantRanks = userGang.getMembers()
		                                       .stream()
		                                       .filter(member -> userOutranks(userRank, member))
		                                       .map(member -> Bukkit.getOfflinePlayer(member.getUuid()))
		                                       .filter(offlinePlayer -> offlinePlayer.getName() != null)
		                                       .map(OfflinePlayer::getName)
		                                       .collect(Collectors.toCollection(ArrayList::new));

		// if no descendants found
		if (descendantRanks.isEmpty()) {
			descendantRanks.add("");
		}

		// return the rank names
		return descendantRanks;
	}

	// The rank tree is head-rooted, so the user outranks a member whose rank node has the user's below it.
	private boolean userOutranks(Rank userRank, Member member) {
		Rank memberRank = member.getRank();

		return memberRank != null && rankManager.getRankTree().isDescendant(memberRank.getNode(), userRank.getNode());
	}

}
