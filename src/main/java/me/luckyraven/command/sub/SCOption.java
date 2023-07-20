package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.rank.Rank;
import me.luckyraven.rank.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

public class SCOption extends CommandHandler {

	private final Gangland gangland;

	public SCOption(Gangland gangland) {
		super(gangland, "option", false);
		this.gangland = gangland;
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {

	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		UserManager<Player> userManager = gangland.getInitializer().getUserManager();
		GangManager         gangManager = gangland.getInitializer().getGangManager();
		RankManager         rankManager = gangland.getInitializer().getRankManager();

		Argument gang = gangArgument(userManager, gangManager, rankManager);

		getArgument().addSubArgument(gang);
	}

	@Override
	protected void help(CommandSender sender, int page) {

	}

	private Argument gangArgument(UserManager<Player> userManager, GangManager gangManager, RankManager rankManager) {
		Argument gang = new Argument("gang", getArgumentTree());

		Argument rank = new Argument("rank", getArgumentTree());

		Argument target = new OptionalArgument(getArgumentTree());

		rank.addSubArgument(target);

		// glw option gang rank <target> <rank>
		Argument rankType = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang userGang = gangManager.getGang(user.getGangId());

			String targetStr  = args[3];
			String rankStr    = args[4];
			UUID   targetUuid = null;
			for (UUID uuid : userGang.getGroup().keySet()) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);

				if (!Objects.requireNonNull(offlinePlayer.getName()).equalsIgnoreCase(targetStr)) continue;

				targetUuid = uuid;
				break;
			}

			if (targetUuid == null) {
				player.sendMessage(MessageAddon.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			// only support promotion
			// cannot promote more than your rank
			if (userGang.getUserRank(player.getUniqueId()).equals(userGang.getUserRank(targetUuid))) {
				player.sendMessage(MessageAddon.GANG_SAME_RANK_ACTION.toString());
				return;
			}

			Rank nextRank = rankManager.get(rankStr);
			userGang.setUserRank(targetUuid, nextRank);

			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetUuid);
			if (offlinePlayer.isOnline()) Objects.requireNonNull(offlinePlayer.getPlayer()).sendMessage(
					MessageAddon.GANG_PROMOTE_TARGET_SUCCESS.toString().replace("%rank%", nextRank.getName()));
			player.sendMessage(MessageAddon.GANG_PROMOTE_PLAYER_SUCCESS.toString()
			                                                           .replace("%player%", targetStr)
			                                                           .replace("%rank%", nextRank.getName()));

			// update database
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof GangDatabase gangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> gangDatabase.updateDataTable(userGang));
					break;
				}
		});

		target.addSubArgument(rankType);

		gang.addSubArgument(rank);

		return gang;
	}

}
