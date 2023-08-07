package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.TriConsumer;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.rank.Rank;
import me.luckyraven.rank.RankManager;
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
		super("demote", tree, parent);

		this.gangland = gangland;
		this.tree = tree;

		this.userManager = userManager;
		this.memberManager = memberManager;
		this.gangManager = gangManager;
		this.rankManager = rankManager;

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

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
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
			Tree.Node<Rank> playerRank = userMember.getRank().getNode();
			Tree.Node<Rank> targetRank = targetMember.getRank().getNode();

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
			if (offlinePlayer.isOnline()) Objects.requireNonNull(offlinePlayer.getPlayer()).sendMessage(
					MessageAddon.GANG_DEMOTE_TARGET_SUCCESS.toString().replace("%rank%", previousRank.getName()));
			player.sendMessage(MessageAddon.GANG_DEMOTE_PLAYER_SUCCESS.toString()
			                                                          .replace("%player%", targetStr)
			                                                          .replace("%rank%", previousRank.getName()));
			targetMember.setRank(previousRank);

			// update database
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof GangDatabase gangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					Member finalTargetMember = targetMember;
					helper.runQueries(database -> gangDatabase.updateMembersTable(finalTargetMember));
					break;
				}
		});
	}

}
