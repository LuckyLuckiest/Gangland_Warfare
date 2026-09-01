package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Objects;

class GangDemoteCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;

	protected GangDemoteCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                            RankManager rankManager) {
		super(gangland, "demote", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.rankManager   = rankManager;

		this.addSubArgument(gangDemote());
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

	private OptionalArgument gangDemote() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Member userMember = memberManager.getMember(player.getUniqueId());

			String  forceRank = String.format("%s.command.gang.force_rank", Gangland.FULL_PREFIX);
			boolean force     = player.hasPermission(forceRank);

			if (!user.hasGang()) {
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

			// change the user rank by proceeding to the next node
			Rank currentRank = targetMember.getRank();
			Rank userRank    = userMember.getRank();
			// cannot demote higher rank
			if (currentRank == null || userRank == null) return;

			Tree.Node<Rank> playerRank = userRank.getNode();
			Tree.Node<Rank> targetRank = currentRank.getNode();

			if (!force) {
				// [player : Owner (descendant), target : Member (ancestor)] (Inverse)
				if (!rankManager.getRankTree().isDescendant(targetRank, playerRank)) {
					user.sendMessage(Messages.GANG_HIGHER_RANK_ACTION.toString());
					return;
				}
			}

			Tree.Node<Rank> previousRankNode = currentRank.getNode().getParent();

			if (previousRankNode == null) {
				user.sendMessage(Messages.GANG_DEMOTE_END.toString());
				return;
			}

			Rank          previousRank  = previousRankNode.getData();
			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());
			String        offlineName   = offlinePlayer.getName();

			if (offlineName != null && !offlineName.isEmpty() && offlinePlayer.isOnline()) {
				Player onlinePlayer = offlinePlayer.getPlayer();
				String message = Messages.GANG_DEMOTE_TARGET_SUCCESS.toString()
				                                                    .replace("%rank%", previousRank.getName());

				// remove the previous rank attachments
				User<Player> onlineUser = userManager.getUser(onlinePlayer);

				if (onlineUser != null) onlineUser.flushPermissions(previousRank);

				Objects.requireNonNull(onlinePlayer).sendMessage(message);
			}

			user.sendMessage(Messages.GANG_DEMOTE_PLAYER_SUCCESS.toString()
			                                                    .replace("%player%", targetStr)
			                                                    .replace("%rank%", previousRank.getName()));
			memberManager.assignRank(targetMember, previousRank);
		}, sender -> GangKickCommand.getDescendantRanks(userManager, memberManager, gangManager, rankManager, sender));
	}

}
