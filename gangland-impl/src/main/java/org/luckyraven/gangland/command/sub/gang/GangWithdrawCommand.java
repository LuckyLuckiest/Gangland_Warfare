package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.NavigableSet;

class GangWithdrawCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;

	protected GangWithdrawCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              UserManager<Player> userManager, MemberManager memberManager,
	                              GangManager gangManager) {
		super(gangland, "withdraw", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;

		this.addSubArgument(gangWithdraw());
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

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<amount>"));
		};
	}

	private OptionalArgument gangWithdraw() {
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
				BigDecimal argAmount = Currency.parse(args[2]);
				Gang       gang      = gangManager.getGang(user.getGangId());

				double rate   = Settings.getGangContributionRate();
				int    length = String.valueOf((int) rate).length() - 1;
				double round  = Math.pow(10, length);

				double contribution = Math.round(argAmount.doubleValue() / rate * round) / round;

				List<User<Player>> gangOnlineMembers = gang.getOnlineMembers(userManager::getUser);


				if (gang.getEconomy().getAmount().compareTo(argAmount) < 0) {
					user.sendMessage(Messages.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
					return;
				}

				user.getEconomy().depositAmount(argAmount);
				gang.getEconomy().withdrawAmount(argAmount);
				// the user can get to negative value
				member.decreaseContribution(contribution);
				for (User<Player> gangUser : gangOnlineMembers) {
					gangUser.getUser()
					        .sendMessage(Messages.GANG_MONEY_WITHDRAW.toString()
					                                                 .replace("%player%", player.getName())
					                                                 .replace("%amount%",
					                                                          Settings.formatAmount(argAmount)));
				}
				user.sendMessage(GanglandChatUtil.color("&c-" + contribution));
			} catch (NumberFormatException exception) {
				user.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", args[2]));
			}
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null || !user.hasGang()) return null;

			Gang           gang    = gangManager.getGang(user.getGangId());
			EconomyHandler economy = gang.getEconomy();
			double         balance = economy.getAmount().doubleValue();

			if (balance <= 0D) return List.of("<amount>");

			NavigableSet<Double> values = NumberUtil.getSetOfNumbers(balance);

			return values.stream()
					.map(value -> Double.parseDouble(value.toString()))
					.map(value -> Math.round(value * 100.0) / 100.0)
					.sorted()
					.map(value -> value % 1 == 0 ? String.valueOf(value.longValue()) : String.format("%.2f", value))
					.toList();
		});
	}

}
