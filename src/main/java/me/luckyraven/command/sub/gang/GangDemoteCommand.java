package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.TriConsumer;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

class GangDemoteCommand extends SubArgument {

	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;

	protected GangDemoteCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super("demote", tree, parent);

		this.tree = tree;

		this.userManager = gangland.getInitializer().getUserManager();
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.gangManager = gangland.getInitializer().getGangManager();
		this.rankManager = gangland.getInitializer().getRankManager();

		this.addSubArgument(gangDemote());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private OptionalArgument gangDemote() {
		return new OptionalArgument(tree, (argument, sender, args) -> {
			Player       player     = (Player) sender;
			User<Player> user       = userManager.getUser(player);
			Member       userMember = memberManager.getMember(player.getUniqueId());

			String forceRank = "gangland.command.gang.force_rank";

			boolean force = player.hasPermission(forceRank);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			String targetStr    = args[2];
			Member targetMember = null;
			for (Member member : gang.getGroup()) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());

				if (!Objects.requireNonNull(offlinePlayer.getName()).equalsIgnoreCase(targetStr)) continue;

				targetMember = member;
				break;
			}

			if (targetMember == null) {
				player.sendMessage(MessageAddon.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			// change the user rank by proceeding to the next node
			Rank currentRank = targetMember.getRank();
			// cannot demote higher rank
			if (currentRank == null || userMember.getRank() == null) return;

			Tree.Node<Rank> playerRank = userMember.getRank().getNode();
			Tree.Node<Rank> targetRank = currentRank.getNode();

			if (!force)
				// [player : Owner (descendant), target : Member (ancestor)] (Inverse)
				if (!rankManager.getRankTree().isDescendant(targetRank, playerRank)) {
					player.sendMessage(MessageAddon.GANG_HIGHER_RANK_ACTION.toString());
					return;
				}

			Tree.Node<Rank> previousRankNode = currentRank.getNode().getParent();

			if (previousRankNode == null) {
				player.sendMessage(MessageAddon.GANG_DEMOTE_END.toString());
				return;
			}

			Rank previousRank = previousRankNode.getData();

			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());
			if (offlinePlayer.getPlayer() != null && offlinePlayer.isOnline()) {
				Player onlinePlayer = offlinePlayer.getPlayer();
				String message = MessageAddon.GANG_DEMOTE_TARGET_SUCCESS.toString().replace("%rank%",
				                                                                            previousRank.getName());

				// remove the previous rank attachments
				User<Player> onlineUser = userManager.getUser(onlinePlayer);

				onlineUser.flushPermissions(previousRank);

				Objects.requireNonNull(onlinePlayer).sendMessage(message);
			}
			player.sendMessage(MessageAddon.GANG_DEMOTE_PLAYER_SUCCESS.toString()
			                                                          .replace("%player%", targetStr)
			                                                          .replace("%rank%", previousRank.getName()));
			targetMember.setRank(previousRank);
		});
	}

}
