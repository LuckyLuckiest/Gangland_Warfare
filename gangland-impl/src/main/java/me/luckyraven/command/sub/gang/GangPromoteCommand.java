package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class GangPromoteCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;

	protected GangPromoteCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "promote", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager   = gangland.getInitializer().getUserManager();
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.gangManager   = gangland.getInitializer().getGangManager();
		this.rankManager   = gangland.getInitializer().getRankManager();

		this.addSubArgument(gangPromote());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				user.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private OptionalArgument gangPromote() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player     = (Player) sender;
			User<Player> user       = userManager.getUser(player);
			Member       userMember = memberManager.getMember(player.getUniqueId());

			String  forceRank = String.format("%s.command.gang.force_rank", gangland.getFullPrefix());
			boolean force     = player.hasPermission(forceRank);

			if (!user.hasGang()) {
				user.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			String targetStr    = args[2];
			Member targetMember = null;
			for (Member member : gang.getGroup()) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
				String        offlineName   = offlinePlayer.getName();

				if (offlineName == null || offlineName.isEmpty() || !offlineName.equalsIgnoreCase(targetStr)) continue;

				targetMember = member;
				break;
			}

			if (targetMember == null) {
				user.sendMessage(MessageAddon.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			// change the user rank by proceeding to the next node
			Rank currentRank = targetMember.getRank();
			// in the case there are more than one child then give options to the promoter
			if (!force) {
				Rank userMemberRank = userMember.getRank();

				if (userMemberRank == null || currentRank == null) return;

				// cannot promote more than your rank
				if (userMemberRank.equals(targetMember.getRank())) {
					user.sendMessage(MessageAddon.GANG_SAME_RANK_ACTION.toString());
					return;
				}

				// check if target has higher rank than user
				Tree.Node<Rank> userNode   = userMemberRank.getNode();
				Tree.Node<Rank> targetNode = currentRank.getNode();

				if (rankManager.getRankTree().isDescendant(targetNode, userNode)) {
					user.sendMessage(MessageAddon.GANG_HIGHER_RANK_ACTION.toString());
					return;
				}
			}

			// navigate the ranks first
			List<Rank> nextRanks = Objects.requireNonNull(rankManager.getRankTree().find(currentRank))
										  .getNode()
										  .getChildren()
										  .stream()
										  .map(Tree.Node::getData)
										  .toList();

			if (nextRanks.isEmpty()) {
				user.sendMessage(MessageAddon.GANG_PROMOTE_END.toString());
				return;
			}

			if (nextRanks.size() > 1) {
				ComponentBuilder ranks = new ComponentBuilder();

				for (int i = 0; i < nextRanks.size(); i++) {
					String rank = nextRanks.get(i).getName();

					var value = String.format("/%s option gang rank %s %s", gangland.getShortPrefix(), targetStr, rank);
					var sep   = new ComponentBuilder(rank).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, value));

					ranks.append(sep.create());

					if (i < nextRanks.size() - 1) ranks.append("  ");
				}
			} else {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());
				String        offlineName   = offlinePlayer.getName();

				Rank first = nextRanks.getFirst();
				if (offlineName != null && !offlineName.isEmpty() && offlinePlayer.isOnline()) {
					Player onlinePlayer = offlinePlayer.getPlayer();
					String message = MessageAddon.GANG_PROMOTE_TARGET_SUCCESS.toString()
																			 .replace("%rank%", first.getName());
					// remove the previous rank attachments
					User<Player> onlineUser = userManager.getUser(onlinePlayer);

					onlineUser.flushPermissions(first);

					Objects.requireNonNull(onlinePlayer).sendMessage(message);
				}

				String string  = MessageAddon.GANG_PROMOTE_PLAYER_SUCCESS.toString();
				String replace = string.replace("%player%", targetStr).replace("%rank%", first.getName());
				user.sendMessage(replace);

				targetMember.setRank(first);
			}
		}, sender -> {
			Player       player     = (Player) sender;
			User<Player> user       = userManager.getUser(player);
			Member       userMember = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				return null;
			}

			Gang userGang = gangManager.getGang(user.getGangId());
			Rank userRank = userMember.getRank();

			if (userRank == null) {
				return null;
			}

			// get the members in the gang
			List<Member> members = userGang.getValue();

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
