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
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

class GangPromoteCommand extends SubArgument {

	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;

	protected GangPromoteCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super("promote", tree, parent);

		this.tree = tree;

		this.userManager = gangland.getInitializer().getUserManager();
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.gangManager = gangland.getInitializer().getGangManager();
		this.rankManager = gangland.getInitializer().getRankManager();

		this.addSubArgument(gangPromote());
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

	private OptionalArgument gangPromote() {
		return new OptionalArgument(tree, (argument, sender, args) -> {
			Player       player     = (Player) sender;
			User<Player> user       = userManager.getUser(player);
			Member       userMember = memberManager.getMember(player.getUniqueId());

			String  forceRank = "gangland.command.gang.force_rank";
			boolean force     = player.hasPermission(forceRank);

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
			// in the case there are more than one child then give options to the promoter
			if (!force) {
				if (userMember.getRank() == null) return;

				// cannot promote more than your rank
				if (userMember.getRank().equals(targetMember.getRank())) {
					player.sendMessage(MessageAddon.GANG_SAME_RANK_ACTION.toString());
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
				player.sendMessage(MessageAddon.GANG_PROMOTE_END.toString());
				return;
			}

			if (nextRanks.size() > 1) {
				ComponentBuilder ranks = new ComponentBuilder();

				for (int i = 0; i < nextRanks.size(); i++) {
					String rank = nextRanks.get(i).getName();

					ComponentBuilder sep = new ComponentBuilder(rank).event(
							new ClickEvent(ClickEvent.Action.RUN_COMMAND,
										   "/glw option gang rank " + targetStr + " " + rank));

					ranks.append(sep.create());

					if (i < nextRanks.size() - 1) ranks.append("  ");
				}
			} else {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());
				if (offlinePlayer.getPlayer() != null && offlinePlayer.isOnline()) {
					Player onlinePlayer = offlinePlayer.getPlayer();
					String message = MessageAddon.GANG_PROMOTE_TARGET_SUCCESS.toString()
																			 .replace("%rank%",
																					  nextRanks.get(0).getName());
					// remove the previous rank attachments
					User<Player> onlineUser = userManager.getUser(onlinePlayer);

					onlineUser.flushPermissions(nextRanks.get(0));

					Objects.requireNonNull(onlinePlayer).sendMessage(message);
				}
				player.sendMessage(MessageAddon.GANG_PROMOTE_PLAYER_SUCCESS.toString()
																		   .replace("%player%", targetStr)
																		   .replace("%rank%",
																					nextRanks.get(0).getName()));

				targetMember.setRank(nextRanks.get(0));
			}
		});
	}

}
