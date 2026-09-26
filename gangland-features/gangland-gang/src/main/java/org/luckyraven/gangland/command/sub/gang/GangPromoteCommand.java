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
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
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

			Gang gang = gangManager.getGang(user.getGangId());

			String targetStr    = args[2];
			Member targetMember = null;
			for (Member member : gang.getMembers()) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
				String        offlineName   = offlinePlayer.getName();

				if (offlineName == null || offlineName.isEmpty() || !offlineName.equalsIgnoreCase(targetStr)) continue;

				targetMember = member;
				break;
			}

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
				ComponentBuilder ranks = new ComponentBuilder();

				for (int i = 0; i < nextRanks.size(); i++) {
					String rank = nextRanks.get(i).getName();

					var value = String.format("/%s option gang rank %s %s", GanglandApi.SHORT_PREFIX, targetStr, rank);
					var sep   = new ComponentBuilder(rank).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, value));

					ranks.append(sep.create());

					if (i < nextRanks.size() - 1) ranks.append("  ");
				}

				player.spigot().sendMessage(ranks.create());
			} else {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());
				String        offlineName   = offlinePlayer.getName();

				Rank first = nextRanks.get(0);
				if (offlineName != null && !offlineName.isEmpty() && offlinePlayer.isOnline()) {
					Player onlinePlayer = offlinePlayer.getPlayer();
					String message = Messages.GANG_PROMOTE_TARGET_SUCCESS.toString()
					                                                     .replace("%rank%", first.getName());
					// remove the previous rank attachments
					User<Player> onlineUser = userManager.getUser(onlinePlayer);

					if (onlineUser != null) {
						RankPermissionApplier.flush(onlineUser, first);
					}

					Objects.requireNonNull(onlinePlayer).sendMessage(message);
				}

				String string  = Messages.GANG_PROMOTE_PLAYER_SUCCESS.toString();
				String replace = string.replace("%player%", targetStr).replace("%rank%", first.getName());
				user.sendMessage(replace);

				memberManager.assignRank(targetMember, first);
			}
		}, sender -> {
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

			// get the members in the gang
			List<Member> members = userGang.getMembers();

			// filter the members by rank
			List<String> descendantRanks = new ArrayList<>();

			for (Member member : members) {
				Rank memberRank = member.getRank();

				if (memberRank == null) continue;

				Tree<Rank> rankTree = rankManager.getRankTree();

				if (!rankTree.isDescendant(memberRank.getNode(), userRank.getNode())) continue;

				OfflinePlayer offlinePlayer     = Bukkit.getOfflinePlayer(member.getUuid());
				String        offlinePlayerName = offlinePlayer.getName();

				if (offlinePlayerName == null) continue;

				descendantRanks.add(offlinePlayer.getName());
			}

			// if no descendants found
			if (descendantRanks.isEmpty()) {
				descendantRanks.add("");
			}

			// return the rank names
			return descendantRanks;
		});
	}

}
