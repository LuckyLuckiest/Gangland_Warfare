package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.TriConsumer;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.rank.Rank;
import me.luckyraven.rank.RankManager;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Types;
import java.util.Objects;
import java.util.UUID;

class GangKickCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;

	protected GangKickCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                          MemberManager memberManager, GangManager gangManager, RankManager rankManager) {
		super("kick", tree, parent);

		this.gangland = gangland;
		this.tree = tree;

		this.userManager = userManager;
		this.memberManager = memberManager;
		this.gangManager = gangManager;
		this.rankManager = rankManager;

		gangKick();
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

	private void gangKick() {
		Argument kickName = new OptionalArgument(tree, (argument, sender, args) -> {
			Player       player     = (Player) sender;
			User<Player> user       = userManager.getUser(player);
			Member       userMember = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			String targetStr    = args[2];
			Member targetMember = null;
			for (Member member : gang.getGroup()) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());

				if (offlinePlayer.getName() == null) continue;

				if (!Objects.requireNonNull(offlinePlayer.getName()).equalsIgnoreCase(targetStr)) continue;

				targetMember = member;
				break;
			}

			if (targetMember == null) {
				player.sendMessage(MessageAddon.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			Tree.Node<Rank> playerRank = userMember.getRank().getNode();
			Tree.Node<Rank> targetRank = targetMember.getRank().getNode();

			if (!rankManager.getRankTree().isDescendant(targetRank, playerRank)) {
				player.sendMessage(MessageAddon.GANG_HIGHER_RANK_ACTION.toString());
				return;
			}

			User<Player>  onlineTarget;
			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());
			if (offlinePlayer.isOnline()) onlineTarget = userManager.getUser(offlinePlayer.getPlayer());
			else onlineTarget = null;

			if (onlineTarget != null) {
				gang.removeMember(onlineTarget, targetMember);
				onlineTarget.getUser().sendMessage(MessageAddon.KICKED_FROM_GANG.toString());
			} else
				// remove user gang data
				gang.removeMember(targetMember);

			// update the user account
			// update the gang data
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases()) {
				if (handler instanceof UserDatabase userDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					if (onlineTarget != null) helper.runQueries(database -> userDatabase.updateDataTable(onlineTarget));
					else {
						UUID finalTargetUuid = targetMember.getUuid();
						helper.runQueries(database -> {
							database.table("data").update("uuid = ?", new Object[]{finalTargetUuid},
							                              new int[]{Types.VARCHAR}, new String[]{"gang_id"},
							                              new Object[]{-1}, new int[]{Types.INTEGER});
						});
					}
				}

				if (handler instanceof GangDatabase gangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					Member finalTargetMember = targetMember;
					helper.runQueries(database -> gangDatabase.updateMembersTable(finalTargetMember));
				}
			}

			player.sendMessage(MessageAddon.GANG_KICKED_TARGET.toString()
			                                                  .replace("%player%", Objects.requireNonNull(
					                                                  offlinePlayer.getName())));
		});

		this.addSubArgument(kickName);
	}

}
