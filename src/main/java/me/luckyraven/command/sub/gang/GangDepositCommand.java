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
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

class GangDepositCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;

	protected GangDepositCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                             UserManager<Player> userManager, MemberManager memberManager,
	                             GangManager gangManager) {
		super("deposit", tree, parent);

		this.gangland = gangland;
		this.tree = tree;

		this.userManager = userManager;
		this.memberManager = memberManager;
		this.gangManager = gangManager;

		this.addSubArgument(gangDeposit());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return null;
	}

	private OptionalArgument gangDeposit() {
		return new OptionalArgument(tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Member       member = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			try {
				double argAmount = Double.parseDouble(args[2]);
				Gang   gang      = gangManager.getGang(user.getGangId());

				double rate   = SettingAddon.getGangContributionRate();
				int    length = String.valueOf((int) rate).length() - 1;
				double round  = Math.pow(10, length);

				double contribution = Math.round(argAmount / rate * round) / round;

				List<User<Player>> gangOnlineMembers = gang.getOnlineMembers(userManager);

				if (user.getBalance() < argAmount) {
					player.sendMessage(MessageAddon.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
					return;
				} else if (gang.getBalance() + argAmount > SettingAddon.getGangMaxBalance()) {
					player.sendMessage(MessageAddon.CANNOT_EXCEED_MAXIMUM.toString());
					return;
				}

				user.setBalance(user.getBalance() - argAmount);
				gang.setBalance(gang.getBalance() + argAmount);
				member.increaseContribution(contribution);
				for (User<Player> gangUser : gangOnlineMembers) {
					gangUser.getUser().sendMessage(MessageAddon.GANG_MONEY_DEPOSIT.toString()
					                                                              .replace("%player%", player.getName())
					                                                              .replace("%amount%",
					                                                                       SettingAddon.formatDouble(
							                                                                       argAmount)));
				}
				player.sendMessage(ChatUtil.color("&a+" + contribution));

				// update database
				for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases()) {
					if (handler instanceof UserDatabase userDatabase) {
						DatabaseHelper helper = new DatabaseHelper(gangland, handler);

						helper.runQueries(database -> userDatabase.updateDataTable(user));
					}

					if (handler instanceof GangDatabase gangDatabase) {
						DatabaseHelper helper = new DatabaseHelper(gangland, handler);

						helper.runQueries(database -> {
							gangDatabase.updateDataTable(gang);
							gangDatabase.updateMembersTable(member);
						});
					}
				}
			} catch (NumberFormatException exception) {
				player.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", args[2]));
			}
		});
	}

}
