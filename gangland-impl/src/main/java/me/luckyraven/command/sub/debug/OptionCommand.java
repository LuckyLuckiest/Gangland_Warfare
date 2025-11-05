package me.luckyraven.command.sub.debug;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
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
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class OptionCommand extends CommandHandler {

	public OptionCommand(Gangland gangland) {
		super(gangland, "option", false);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) { }

	@Override
	protected void initializeArguments() {
		UserManager<Player> userManager   = getGangland().getInitializer().getUserManager();
		MemberManager       memberManager = getGangland().getInitializer().getMemberManager();
		GangManager         gangManager   = getGangland().getInitializer().getGangManager();
		RankManager         rankManager   = getGangland().getInitializer().getRankManager();

		Argument gang     = gangArgument(userManager, memberManager, gangManager, rankManager);
		Argument resource = resourcePack();

		getArgument().addSubArgument(gang);
		getArgument().addSubArgument(resource);
	}

	@Override
	protected void help(CommandSender sender, int page) { }

	private Argument resourcePack() {
		Argument click = new Argument(getGangland(), "click", getArgumentTree());

		// glw option click resource
		Argument resource = new Argument(getGangland(), "resource", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.prefixMessage(
					"&7You should first disconnect from the server and click the server tab you created, " +
					"next click &8(&bEdit&8)&7 then change &8(&bServer Resource Packs&8)&7 to either " +
					"&aPrompt&7 or &aEnabled&7 then click &8(&bDone&8)&7 button and log back in."));
			sender.sendMessage(ChatUtil.color(
					"&7If you chose &aPrompt &7then click &8(&bYes&8)&7 once you joined back into the server, " +
					"then the download process will start."));
		});

		click.addSubArgument(resource);

		return click;
	}

	private Argument gangArgument(UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
								  RankManager rankManager) {
		Argument gang = new Argument(getGangland(), "gang", getArgumentTree());

		Argument rank = new Argument(getGangland(), "rank", getArgumentTree());

		Argument target = new OptionalArgument(getGangland(), getArgumentTree());

		rank.addSubArgument(target);

		// glw option gang rank <target> <rank>
		Argument rankType = new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			Player       player     = (Player) sender;
			User<Player> user       = userManager.getUser(player);
			Member       userMember = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang userGang = gangManager.getGang(user.getGangId());

			String targetStr    = args[3];
			String rankStr      = args[4];
			Member targetMember = null;
			for (Member member : userGang.getGroup()) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
				String        offlineName   = offlinePlayer.getName();

				if (offlineName == null || offlineName.isEmpty() || !offlineName.equalsIgnoreCase(targetStr)) continue;

				targetMember = member;
				break;
			}

			if (targetMember == null) {
				player.sendMessage(MessageAddon.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			if (userMember.getRank() == null) return;
			// only support promotion
			// cannot promote more than your rank

			if (userMember.getRank().equals(targetMember.getRank())) {
				player.sendMessage(MessageAddon.GANG_SAME_RANK_ACTION.toString());
				return;
			}

			Rank nextRank = rankManager.get(rankStr);

			if (nextRank == null) return;

			targetMember.setRank(nextRank);

			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());
			Player        onlinePlayer  = offlinePlayer.getPlayer();

			if (onlinePlayer != null && offlinePlayer.isOnline()) {
				onlinePlayer.sendMessage(
						MessageAddon.GANG_PROMOTE_TARGET_SUCCESS.toString().replace("%rank%", nextRank.getName()));
			}

			player.sendMessage(MessageAddon.GANG_PROMOTE_PLAYER_SUCCESS.toString()
																	   .replace("%player%", targetStr)
																	   .replace("%rank%", nextRank.getName()));
		});

		target.addSubArgument(rankType);

		gang.addSubArgument(rank);

		return gang;
	}

}
