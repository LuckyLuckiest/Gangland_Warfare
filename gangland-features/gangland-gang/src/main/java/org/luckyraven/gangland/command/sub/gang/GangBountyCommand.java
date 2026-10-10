package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.events.gang.GangBountyEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.util.TriConsumer;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code /glw gang bounty <gang> <amount>} (D8 = B): a player, in a gang or not, puts money from his own balance on a
 * rival gang. The bounty is paid to whichever gang takes one of that gang's turfs (TurfGangBountyListener). Refused for
 * his own gang, an allied gang, an unknown gang, a zero, negative or too small amount, or more than his balance. A
 * cancelled post refunds him.
 */
final class GangBountyCommand extends SubArgument {

	private final JavaPlugin            gangland;
	private final Tree<Argument>        tree;
	private final UserManager<Player>   userManager;
	private final MemberManager         memberManager;
	private final GangManager           gangManager;
	private final GangMessages          messages;

	GangBountyCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                  @Qualifier("online") UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                  GangMessages messages) {
		super(gangland, "bounty", tree, parent);
		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.messages      = messages;

		this.addSubArgument(gangArgument());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(GanglandChatUtil.setArguments(
				Messages.ARGUMENTS_MISSING.toString(), "<gang> <amount>"));
	}

	private OptionalArgument gangArgument() {
		OptionalArgument gang = new OptionalArgument(gangland, tree, (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<amount>")),
		                                             this::otherGangNames);

		gang.addSubArgument(amountArgument());
		return gang;
	}

	private OptionalArgument amountArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> post(sender, args),
		                            sender -> List.of("<amount>"));
	}

	private void post(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			return;
		}

		User<Player> user = userManager.getUser(player);
		if (user == null) {
			return;
		}

		Gang target = find(args[2]);
		if (target == null) {
			player.sendMessage(messages.text("Bounty.Unknown", "&cNo gang named %gang%.", "gang", args[2]));
			return;
		}

		Gang own = ownGang(player);
		if (own != null && own.getId() == target.getId()) {
			player.sendMessage(messages.text("Bounty.Own_Gang", "&cYou cannot put a bounty on your own gang."));
			return;
		}
		if (own != null && own.isAlly(target)) {
			player.sendMessage(messages.text("Bounty.Ally", "&cYou cannot put a bounty on an allied gang."));
			return;
		}

		BigDecimal amount;
		try {
			amount = Currency.parse(args[3]);
		} catch (NumberFormatException exception) {
			player.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", args[3]));
			return;
		}

		if (amount.signum() <= 0) {
			player.sendMessage(Messages.CANNOT_TAKE_LESS_THAN_ZERO.toString());
			return;
		}

		// same minimum as the admin bounty command (BountySetCommand)
		BigDecimal minimum = Currency.of(Settings.getBountyMinimum());
		if (minimum.signum() > 0 && amount.compareTo(minimum) < 0) {
			player.sendMessage(Messages.BOUNTY_BELOW_MINIMUM.toString()
			                                                .replace("%money_symbol%", Settings.getMoneySymbol())
			                                                .replace("%minimum%", Settings.formatAmount(minimum)));
			return;
		}

		if (user.getEconomy().getAmount().compareTo(amount) < 0) {
			player.sendMessage(Messages.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
			return;
		}

		user.getEconomy().withdrawAmount(amount);

		GangBountyEvent event = new GangBountyEvent(false, target, amount);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			// the poster already paid: a cancelled post must not eat the money
			user.getEconomy().depositAmount(amount);
			return;
		}

		target.getBounty().addBounty(player, amount);

		player.sendMessage(messages.text("Bounty.Posted",
		                                 "&aPosted %money_symbol%%amount% on %gang%. &7The gang that takes one of their turfs collects %money_symbol%%total%.",
		                                 "money_symbol", Settings.getMoneySymbol(),
		                                 "amount", Settings.formatAmount(amount),
		                                 "gang", target.getName(),
		                                 "total", Settings.formatAmount(target.getBounty().getAmount())));
	}

	/** Plain gang names, every gang except the sender's own. */
	private List<String> otherGangNames(CommandSender sender) {
		if (!(sender instanceof Player player)) {
			return List.of();
		}

		Gang own = ownGang(player);
		return gangManager.getAll()
				.stream()
				.filter(gang -> gang.getName() != null && (own == null || gang.getId() != own.getId()))
				.map(gang -> GangNameRules.plain(gang.getName()))
				.toList();
	}

	private @Nullable Gang ownGang(Player player) {
		User<Player> user = userManager.getUser(player);
		if (user == null || !user.hasGang()) {
			return null;
		}
		return gangManager.getGang(user.getGangId());
	}

	/** The same plain-name match GangInfoCommand uses; ids are not accepted here. */
	private @Nullable Gang find(String key) {
		String plain = GangNameRules.plain(key);
		for (Gang gang : gangManager.getAll()) {
			if (gang.getName() != null && GangNameRules.plain(gang.getName()).equalsIgnoreCase(plain)) {
				return gang;
			}
		}
		return null;
	}

}
