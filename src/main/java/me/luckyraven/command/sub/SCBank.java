package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.account.Account;
import me.luckyraven.account.type.Bank;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.ConfirmArgument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class SCBank extends CommandHandler {

	private final Gangland gangland;

	public SCBank(Gangland gangland) {
		super(gangland, "bank", true);
		this.gangland = gangland;

		List<CommandInformation> list = getCommands().entrySet()
		                                             .stream()
		                                             .filter(entry -> entry.getKey().startsWith("bank"))
		                                             .sorted(Map.Entry.comparingByKey())
		                                             .map(Map.Entry::getValue)
		                                             .toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(CommandSender commandSender, String[] arguments) {
		Player       player = (Player) commandSender;
		User<Player> user   = gangland.getInitializer().getUserManager().getUser(player);

		if (user.hasBank()) {
			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank bank) {
					player.sendMessage(ChatUtil.color(String.format("&7%s &3bank information", player.getName())));
					player.sendMessage(ChatUtil.color(String.format("&7Name&8:&7 %s", bank.getName())));
					player.sendMessage(ChatUtil.color(String.format("&7Balance&8:&7 %.2f", bank.getBalance())));
					break;
				}
		} else help(commandSender, 1);
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		YamlConfiguration   message     = gangland.getInitializer().getLanguageLoader().getMessage();
		UserManager<Player> userManager = gangland.getInitializer().getUserManager();

		// create bank
		// glw bank create name
		Argument create = new Argument("create", getArgumentTree(), (sender, args) -> {
			Player player = (Player) sender;
			if (userManager.getUser(player).hasBank()) {
				player.sendMessage(MessageAddon.HAVE_BANK);
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING, "<name>"));
		});

		AtomicReference<String> createBankName = new AtomicReference<>("");

		ConfirmArgument confirmCreate = new ConfirmArgument(getArgumentTree(), (sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.hasBank()) {
				player.sendMessage(MessageAddon.HAVE_BANK);
				return;
			}

			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof UserDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						database.table("bank").update("uuid = ?", new Object[]{player.getUniqueId()},
						                              new int[]{Types.CHAR}, new String[]{"name"},
						                              new Object[]{createBankName.get()}, new int[]{Types.VARCHAR});
						database.table("data").update("uuid = ?", new Object[]{player.getUniqueId()},
						                              new int[]{Types.CHAR}, new String[]{"has_bank"},
						                              new Object[]{true}, new int[]{Types.BOOLEAN});
					});

					player.sendMessage(MessageAddon.CREATED_BANK.replace("%bank%", createBankName.get()));
					user.setHasBank(true);
					break;
				}
			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank bank) bank.setName(createBankName.get());

			createBankName.set("");
		});

		create.addSubArgument(confirmCreate);

		Argument createName = new OptionalArgument(getArgumentTree(), (sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.hasBank()) {
				player.sendMessage(MessageAddon.HAVE_BANK);
				return;
			}

			createBankName.set(args[2]);

			// Need to notify the player and give access to confirm
			player.sendMessage(MessageAddon.BANK_CREATE_FEE);
			player.sendMessage(ChatUtil.color(MessageAddon.confirmCommand(new String[]{"bank", "create"})));
			confirmCreate.setConfirmed(true);
		});

		create.addSubArgument(createName);


		// delete bank
		// glw bank delete
		Argument delete = new Argument("delete", getArgumentTree(), (sender, args) -> {
			Player player = (Player) sender;

			User<Player> user = userManager.getUser(player);
			if (!user.hasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK);
				return;
			}

			// retrieve the name
			String bankName = "";
			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank bank) {
					user.setBalance(user.getBalance() + bank.getBalance());
					bankName = bank.getName();

					bank.setName("");
					bank.setBalance(0D);
					break;
				}

			// save the data in the database
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof UserDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						database.table("bank").update("uuid = ?", new Object[]{player.getUniqueId()},
						                              new int[]{Types.CHAR}, new String[]{"name", "balance"},
						                              new Object[]{"", 0D}, new int[]{Types.VARCHAR, Types.DOUBLE});
						database.table("data").update("uuid = ?", new Object[]{player.getUniqueId()},
						                              new int[]{Types.CHAR}, new String[]{"has_bank"},
						                              new Object[]{false}, new int[]{Types.BOOLEAN});
					});

					player.sendMessage(MessageAddon.REMOVED_BANK.replace("%bank%", bankName));
					user.setHasBank(false);
					break;
				}
		});

		// deposit money to bank
		// glw bank deposit 10
		Argument deposit = new Argument("deposit", getArgumentTree(), (sender, args) -> {
			Player player = (Player) sender;

			User<Player> user = userManager.getUser(player);
			if (!user.hasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK);
				return;
			}

			sender.sendMessage(CommandManager.setArguments(
					Objects.requireNonNull(message.getString("Commands.Syntax.Missing_Arguments")), "<amount>"));
		});

		Argument depositAmount = new OptionalArgument(getArgumentTree(), (sender, args) -> {
			Player player = (Player) sender;

			User<Player> user = userManager.getUser(player);
			if (!user.hasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK);
				return;
			}

			try {
				double amount = Double.parseDouble(args[2]);
				for (Account<?, ?> account : user.getLinkedAccounts())
					if (account instanceof Bank bank) {
						processMoney(user, player, bank, user.getBalance(), amount, user.getBalance() + amount,
						             user.getBalance() - amount);
						break;
					}
			} catch (NumberFormatException exception) {
				player.sendMessage(MessageAddon.MUSTBE_NUMBER);
			}
		});

		deposit.addSubArgument(depositAmount);

		// withdraw money from bank
		// glw bank withdraw 10
		Argument withdraw = new Argument("withdraw", getArgumentTree(), (sender, args) -> {
			Player player = (Player) sender;

			User<Player> user = userManager.getUser(player);
			if (!user.hasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK);
				return;
			}

			sender.sendMessage(CommandManager.setArguments(
					Objects.requireNonNull(message.getString("Commands.Syntax.Missing_Arguments")), "<amount>"));
		});

		Argument withdrawAmount = new OptionalArgument(getArgumentTree(), (sender, args) -> {
			Player player = (Player) sender;

			User<Player> user = userManager.getUser(player);
			if (!user.hasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK);
				return;
			}

			try {
				double amount = Double.parseDouble(args[2]);

				for (Account<?, ?> account : user.getLinkedAccounts())
					if (account instanceof Bank bank) {
						processMoney(user, player, bank, bank.getBalance(), amount, user.getBalance() - amount,
						             user.getBalance() + amount);
						break;
					}
			} catch (NumberFormatException exception) {
				player.sendMessage(MessageAddon.MUSTBE_NUMBER);
			}
		});

		withdraw.addSubArgument(withdrawAmount);

		// balance of gang
		// glw bank balance
		Argument balance = new Argument(new String[]{"balance", "bal"}, getArgumentTree(), (sender, args) -> {
			Player player = (Player) sender;

			User<Player> user = userManager.getUser(player);
			if (!user.hasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK);
				return;
			}

			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank bank) {
					player.sendMessage(MessageAddon.BANK_PLAYER_BALANCE.replace("%balance%", MessageAddon.formatDouble(
							bank.getBalance())));
				}
		});

		// add sub arguments
		getArgument().addSubArgument(create);
		getArgument().addSubArgument(delete);
		getArgument().addSubArgument(deposit);
		getArgument().addSubArgument(withdraw);
		getArgument().addSubArgument(balance);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Bank");
	}

	private void processMoney(User<Player> user, Player player, Bank bank, double check, double amount, double inBank,
	                          double inAccount) {
		if (check == 0D) player.sendMessage(MessageAddon.CANNOT_TAKE_LESSTHANZERO);
		else if (amount > check) player.sendMessage(MessageAddon.CANNOT_TAKE_MORETHANBALANCE);
		else {
			user.setBalance(inAccount);
			bank.setBalance(inBank);

			moneyInDatabase(player, inBank, inAccount);
		}
	}

	private void moneyInDatabase(Player player, double amountInBank, double amountInAccount) {
		for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
			if (handler instanceof UserDatabase) {
				DatabaseHelper helper = new DatabaseHelper(gangland, handler);

				helper.runQueries(database -> {
					database.table("bank").update("uuid = ?", new Object[]{player.getUniqueId()}, new int[]{Types.CHAR},
					                              new String[]{"balance"}, new Object[]{amountInBank},
					                              new int[]{Types.DOUBLE});
					database.table("account").update("uuid = ?", new Object[]{player.getUniqueId()},
					                                 new int[]{Types.CHAR}, new String[]{"balance"},
					                                 new Object[]{amountInAccount}, new int[]{Types.DOUBLE});
				});
				break;
			}
	}

}
