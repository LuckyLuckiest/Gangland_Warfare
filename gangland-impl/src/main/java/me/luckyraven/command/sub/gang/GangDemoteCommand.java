package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.member.Member;
import me.luckyraven.data.account.gang.member.MemberManager;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
			targetMember.setRank(previousRank);
		}, sender -> GangKickCommand.getDescendantRanks(userManager, memberManager, gangManager, rankManager, sender));
	}

}
