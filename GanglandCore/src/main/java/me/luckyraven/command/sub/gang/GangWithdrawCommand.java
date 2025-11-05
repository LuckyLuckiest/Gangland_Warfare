package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

class GangWithdrawCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;

	protected GangWithdrawCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "withdraw", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager   = gangland.getInitializer().getUserManager();
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.gangManager   = gangland.getInitializer().getGangManager();

		this.addSubArgument(gangWithdraw());
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

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>"));
		};
	}

	private OptionalArgument gangWithdraw() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
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


				if (gang.getEconomy().getBalance() < argAmount) {
					player.sendMessage(MessageAddon.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
					return;
				}

				user.getEconomy().deposit(argAmount);
				gang.getEconomy().withdraw(argAmount);
				// the user can get to negative value
				member.decreaseContribution(contribution);
				for (User<Player> gangUser : gangOnlineMembers) {
					gangUser.getUser()
							.sendMessage(MessageAddon.GANG_MONEY_WITHDRAW.toString()
																		 .replace("%player%", player.getName())
																		 .replace("%amount%", SettingAddon.formatDouble(
																				 argAmount)));
				}
				player.sendMessage(ChatUtil.color("&c-" + contribution));
			} catch (NumberFormatException exception) {
				player.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", args[2]));
			}
		});
	}

}
