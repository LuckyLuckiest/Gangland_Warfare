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
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.timer.CountdownTimer;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.sql.Types;
import java.util.HashMap;
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
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player       player = (Player) commandSender;
		User<Player> user   = gangland.getInitializer().getUserManager().getUser(player);

		if (user.hasBank()) {
			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank bank) {
					player.sendMessage(ChatUtil.color(String.format("&6%s&7 bank information", player.getName()),
					                                  String.format("&7%-10s&8: &a%s", "Name", bank.getName()),
					                                  String.format("&7%-10s&8: &a%s%s", "Balance",
					                                                SettingAddon.getMoneySymbol(),
					                                                MessageAddon.formatDouble(bank.getBalance()))));
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
		Argument create = new Argument("create", getArgumentTree(), (argument, sender, args) -> {
			Player player = (Player) sender;
			if (userManager.getUser(player).hasBank()) {
				player.sendMessage(MessageAddon.HAVE_BANK);
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING, "<name>"));
		});

		HashMap<User<?>, AtomicReference<String>> createBankName = new HashMap<>();

		ConfirmArgument confirmCreate = new ConfirmArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.hasBank()) {
				player.sendMessage(MessageAddon.HAVE_BANK);
				return;
			}

			if (user.getBalance() <= SettingAddon.getBankCreateFee()) {
				player.sendMessage(MessageAddon.CANNOT_CREATE_BANK);
				return;
			}

			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof UserDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						database.table("bank").update("uuid = ?", new Object[]{player.getUniqueId()},
						                              new int[]{Types.CHAR}, new String[]{"name"},
						                              new Object[]{createBankName.get(user).get()},
						                              new int[]{Types.VARCHAR});
						database.table("data").update("uuid = ?", new Object[]{player.getUniqueId()},
						                              new int[]{Types.CHAR}, new String[]{"has_bank"},
						                              new Object[]{true}, new int[]{Types.BOOLEAN});
						user.setBalance(user.getBalance() - SettingAddon.getBankCreateFee());
						database.table("account").update("uuid = ?", new Object[]{player.getUniqueId()},
						                                 new int[]{Types.CHAR}, new String[]{"balance"},
						                                 new Object[]{user.getBalance()}, new int[]{Types.DOUBLE});
					});

					player.sendMessage(MessageAddon.BANK_CREATED.replace("%bank%", createBankName.get(user).get()));
					user.setHasBank(true);
					break;
				}

			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank bank) {
					bank.setName(createBankName.get(user).get());
					break;
				}

			createBankName.remove(user);
		});

		create.addSubArgument(confirmCreate);

		Argument createName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.hasBank()) {
				player.sendMessage(MessageAddon.HAVE_BANK);
				return;
			}

			if (confirmCreate.isConfirmed()) return;

			createBankName.put(user, new AtomicReference<>(args[2]));

			// Need to notify the player and give access to confirm
			player.sendMessage(MessageAddon.BANK_CREATE_FEE);
			player.sendMessage(ChatUtil.color(MessageAddon.confirmCommand(new String[]{"bank", "create"})));
			confirmCreate.setConfirmed(true);

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
				sender.sendMessage(
						MessageAddon.BANK_CREATE_CONFIRM.replace("%timer%", String.valueOf(time.getDuration())));
			}, null, time -> {
				confirmCreate.setConfirmed(false);
				createBankName.remove(user);
			});

			timer.start();
		});

		create.addSubArgument(createName);


		// delete bank
		// glw bank delete
		HashMap<User<?>, AtomicReference<String>> deleteBankName = new HashMap<>();

		ConfirmArgument confirmDelete = new ConfirmArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank bank) {
					user.setBalance(user.getBalance() + bank.getBalance() + SettingAddon.getBankCreateFee() / 2);

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
						database.table("account").update("uuid = ?", new Object[]{player.getUniqueId()},
						                                 new int[]{Types.CHAR}, new String[]{"balance"},
						                                 new Object[]{user.getBalance()}, new int[]{Types.DOUBLE});
					});

					player.sendMessage(MessageAddon.BANK_REMOVED.replace("%bank%", deleteBankName.get(user).get()));
					user.setHasBank(false);
					break;
				}

			deleteBankName.remove(user);
		});

		Argument delete = new Argument("delete", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK);
				return;
			}

			if (confirmDelete.isConfirmed()) return;

			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank bank) {
					deleteBankName.put(user, new AtomicReference<>(bank.getName()));
					break;
				}

			player.sendMessage(ChatUtil.color(MessageAddon.confirmCommand(new String[]{"bank", "delete"})));
			confirmDelete.setConfirmed(true);

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
				sender.sendMessage(
						MessageAddon.BANK_REMOVE_CONFIRM.replace("%timer%", String.valueOf(time.getDuration())));
			}, null, time -> {
				confirmDelete.setConfirmed(false);
				deleteBankName.remove(user);
			});

			timer.start();
		});

		delete.addSubArgument(confirmDelete);

		// deposit money to bank
		// glw bank deposit 10
		Argument deposit = new Argument(new String[]{"deposit", "add"}, getArgumentTree(), (argument, sender, args) -> {
			Player player = (Player) sender;

			User<Player> user = userManager.getUser(player);
			if (!user.hasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK);
				return;
			}

			sender.sendMessage(CommandManager.setArguments(
					Objects.requireNonNull(message.getString("Commands.Syntax.Missing_Arguments")), "<amount>"));
		});

		// withdraw money from bank
		// glw bank withdraw 10
		Argument withdraw = new Argument(new String[]{"withdraw", "take"}, getArgumentTree(),
		                                 (argument, sender, args) -> {
			                                 Player player = (Player) sender;

			                                 User<Player> user = userManager.getUser(player);
			                                 if (!user.hasBank()) {
				                                 player.sendMessage(MessageAddon.MUST_CREATE_BANK);
				                                 return;
			                                 }

			                                 sender.sendMessage(CommandManager.setArguments(Objects.requireNonNull(
					                                                                                message.getString("Commands.Syntax.Missing_Arguments")),
			                                                                                "<amount>"));
		                                 });

		Argument amount = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK);
				return;
			}

			try {
				double argAmount = Double.parseDouble(args[2]);

				for (Account<?, ?> account : user.getLinkedAccounts())
					if (account instanceof Bank bank) {
						switch (args[1].toLowerCase()) {
							case "deposit", "add" -> {
								processMoney(user, player, bank, user.getBalance(), argAmount,
								             bank.getBalance() + argAmount, user.getBalance() - argAmount,
								             MessageAddon.BANK_PLAYER_MONEY_ADD.replace("%amount%",
								                                                        MessageAddon.formatDouble(
										                                                        argAmount)));
							}
							case "withdraw", "take" -> {
								processMoney(user, player, bank, bank.getBalance(), argAmount,
								             bank.getBalance() - argAmount, user.getBalance() + argAmount,
								             MessageAddon.BANK_PLAYER_MONEY_TAKE.replace("%amount%",
								                                                         MessageAddon.formatDouble(
										                                                         argAmount)));
							}
						}
						break;
					}
			} catch (NumberFormatException exception) {
				player.sendMessage(MessageAddon.MUSTBE_NUMBER);
			}
		});

		deposit.addSubArgument(amount);
		withdraw.addSubArgument(amount);

		// balance of gang
		// glw bank balance
		Argument balance = new Argument(new String[]{"balance", "bal"}, getArgumentTree(), (argument, sender, args) -> {
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
	                          double inAccount, String message) {
		if (check == 0D) player.sendMessage(MessageAddon.CANNOT_TAKE_LESSTHANZERO);
		else if (amount > check) player.sendMessage(MessageAddon.CANNOT_TAKE_MORETHANBALANCE);
		else {
			user.setBalance(inAccount);
			bank.setBalance(inBank);

			moneyInDatabase(player, inBank, inAccount);
			player.sendMessage(message);
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
