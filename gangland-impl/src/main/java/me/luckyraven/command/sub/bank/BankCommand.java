package me.luckyraven.command.sub.bank;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.copsncrooks.npc.banker.tier.BankTierRegistry;
import me.luckyraven.core.autowire.bean.Qualifier;
import me.luckyraven.core.command.CommandHandler;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.economy.bank.Bank;
import me.luckyraven.economy.bank.Currency;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

	private final UserManager<Player> userManager;
	private final GanglandDatabase    ganglandDatabase;
	private final BankTierRegistry    tierRegistry;

	public BankCommand(Gangland gangland,
	                   @Qualifier("online") UserManager<Player> userManager,
	                   GanglandDatabase ganglandDatabase,
	                   BankTierRegistry tierRegistry) {
		super(gangland, "bank", true);

		this.userManager      = userManager;
		this.ganglandDatabase = ganglandDatabase;
		this.tierRegistry     = tierRegistry;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("bank"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	static boolean processMoney(User<Player> user, Bank bank,
	                            BigDecimal check, BigDecimal amount,
	                            BigDecimal inBank, BigDecimal inAccount) {
		if (check.signum() == 0) {
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

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(deposit);
		arguments.add(withdraw);
		arguments.add(balance);
		arguments.add(resetCap);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Bank");
	}

}
