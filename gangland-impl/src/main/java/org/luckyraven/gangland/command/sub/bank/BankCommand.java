package org.luckyraven.gangland.command.sub.bank;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.copsncrooks.npc.banker.tier.BankTierRegistry;
import org.luckyraven.gangland.copsncrooks.npc.banker.view.BankerFlow;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class BankCommand extends Command {

	/**
	 * Holders of this permission bypass the daily deposit / withdraw caps on every bank path — chat commands (/glw bank
	 * deposit / withdraw) and the Banker NPC GUI alike. Registered with the {@code PermissionManager} from
	 * {@code BankerConfig} so it shows up in rank permission autocompletion.
	 */
	public static final String BYPASS_CAP_PERMISSION = "gangland.bank.bypass_cap";

	/**
	 * Gates the cross-player forms {@code /glw bank deposit <amount> <player>} and
	 * {@code /glw bank withdraw <amount> <player>}, which credit or destroy money in someone else's bank
	 * without touching the sender's balance. Keystone derives a permission per {@code SubArgument} but not
	 * per {@code OptionalArgument} leaf, so without this node both forms inherit
	 * {@code gangland.command.bank.deposit} / {@code .withdraw} — i.e. anyone who may bank at all could mint
	 * money for others. Registered with the {@code PermissionManager} from {@code BankerConfig}.
	 */
	public static final String ADMIN_PERMISSION = "gangland.command.bank.admin";

	private final UserManager<Player> userManager;
	private final GanglandDatabase    ganglandDatabase;
	private final BankTierRegistry    tierRegistry;
	private final BankerFlow          bankerFlow;

	public BankCommand(Gangland gangland,
	                   @Qualifier("online") UserManager<Player> userManager,
	                   GanglandDatabase ganglandDatabase,
	                   BankTierRegistry tierRegistry,
	                   BankerFlow bankerFlow) {
		super(gangland, "bank", true);

		this.userManager      = userManager;
		this.ganglandDatabase = ganglandDatabase;
		this.tierRegistry     = tierRegistry;
		this.bankerFlow       = bankerFlow;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("bank"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	/**
	 * Moves money between a player's cash and their bank after checking the amount and the source balance.
	 *
	 * @param user      the owning user, messaged on refusal
	 * @param bank      the user's bank
	 * @param check     the source balance the amount is taken from (cash on deposit, bank on withdraw)
	 * @param amount    the requested amount; must be strictly positive
	 * @param inBank    the bank balance to write when the move is allowed
	 * @param inAccount the cash balance to write when the move is allowed
	 *
	 * @return {@code true} when both balances were written, {@code false} when the move was refused
	 */
	public static boolean processMoney(User<Player> user, Bank bank,
	                                   BigDecimal check, BigDecimal amount,
	                                   BigDecimal inBank, BigDecimal inAccount) {
		if (amount.signum() <= 0) {
			// Last line of defence: every caller parses through ParsedAmount, but a zero or negative amount
			// reaching here would move money the wrong way (deposit -N credits cash, withdraw -N credits the bank).
			user.getUser().sendMessage(Messages.CANNOT_TAKE_LESS_THAN_ZERO.toString());
			return false;
		} else if (check.signum() == 0) {
			user.getUser().sendMessage(Messages.CANNOT_TAKE_LESS_THAN_ZERO.toString());
			return false;
		} else if (amount.compareTo(check) > 0) {
			user.getUser().sendMessage(Messages.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
			return false;
		} else {
			user.getEconomy().setAmount(Currency.of(inAccount));
			bank.getEconomy().setAmount(Currency.of(inBank));
		}

		return true;
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player       player = (Player) commandSender;
		User<Player> user   = userManager.getUser(player);

		if (user == null) return;

		Bank bank = user.getBank();

		if (!user.hasBank() || bank == null) {
			help(commandSender, 1);
			return;
		}

		user.sendMessage(String.format("&6%s&7 bank information", player.getName()),
		                 String.format("&7%s&8: &a%s", "Name", bank.getName()),
		                 String.format("&7%s&8: &a%s%s", "Balance", Settings.getMoneySymbol(),
		                               Settings.formatAmount(bank.getEconomy().getAmount())));
	}

	@Override
	protected void initializeArguments() {
		BankCreateCommand create = new BankCreateCommand(getGangland(), getArgumentTree(), getArgument(), userManager);
		BankDepositCommand deposit = new BankDepositCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                    userManager, ganglandDatabase, tierRegistry);
		BankWithdrawCommand withdraw = new BankWithdrawCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                       userManager, ganglandDatabase);
		BankBalanceCommand balance = new BankBalanceCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                    userManager);
		BankResetCapCommand resetCap = new BankResetCapCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                       userManager, ganglandDatabase);
		BankMenuCommand menu = new BankMenuCommand(getGangland(), getArgumentTree(), getArgument(), bankerFlow);

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(deposit);
		arguments.add(withdraw);
		arguments.add(balance);
		arguments.add(resetCap);
		arguments.add(menu);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Bank");
	}

}
