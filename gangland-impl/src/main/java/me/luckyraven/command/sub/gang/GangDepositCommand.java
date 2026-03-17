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
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.util.utilities.NumberUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.NavigableSet;

class GangDepositCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;

	protected GangDepositCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "deposit", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager   = gangland.getInitializer().getUserManager();
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.gangManager   = gangland.getInitializer().getGangManager();

		this.addSubArgument(gangDeposit());
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

			sender.sendMessage(ChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<amount>"));
		};
	}

	private OptionalArgument gangDeposit() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Member member = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			try {
				double argAmount = Double.parseDouble(args[2]);
				Gang   gang      = gangManager.getGang(user.getGangId());

				double rate   = Settings.getGangContributionRate();
				int    length = String.valueOf((int) rate).length() - 1;
				double round  = Math.pow(10, length);

				double contribution = Math.round(argAmount / rate * round) / round;

				List<User<Player>> gangOnlineMembers = gang.getOnlineMembers(userManager);

				if (user.getEconomy().getBalance() < argAmount) {
					user.sendMessage(Messages.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
					return;
				} else if (gang.getEconomy().getBalance() + argAmount > Settings.getGangMaxBalance()) {
					user.sendMessage(Messages.CANNOT_EXCEED_MAXIMUM.toString());
					return;
				}

				user.getEconomy().withdraw(argAmount);
				gang.getEconomy().deposit(argAmount);
				member.increaseContribution(contribution);
				for (User<Player> gangUser : gangOnlineMembers) {
					gangUser.getUser()
							.sendMessage(Messages.GANG_MONEY_DEPOSIT.toString()
																	.replace("%player%", player.getName())
																	.replace("%amount%",
																			 Settings.formatDouble(argAmount)));
				}
				user.sendMessage(ChatUtil.color("&a+" + contribution));
			} catch (NumberFormatException exception) {
				user.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", args[2]));
			}
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null || !user.hasGang()) return null;

			EconomyHandler economy = user.getEconomy();
			double         balance = economy.getBalance();

			if (balance <= 0D) return List.of("<amount>");

			NavigableSet<Double> values = NumberUtil.getSetOfNumbers(balance);

			return values.stream()
					.map(value -> String.valueOf(value % 1D == 0D ? (long) value.doubleValue() : value))
					.sorted()
					.toList();
		});
	}

}
