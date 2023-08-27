package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.ConfirmArgument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.account.Account;
import me.luckyraven.data.account.type.Bank;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TimeUtil;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class BankCommand extends CommandHandler {

	public BankCommand(Gangland gangland) {
		super(gangland, "bank", true);

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
		User<Player> user   = getGangland().getInitializer().getUserManager().getUser(player);

		if (user.isHasBank()) {
			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank bank) {
					player.sendMessage(ChatUtil.color(String.format("&6%s&7 bank information", player.getName()),
					                                  String.format("&7%s&8: &a%s", "Name", bank.getName()),
					                                  String.format("&7%s&8: &a%s%s", "Balance",
					                                                SettingAddon.getMoneySymbol(),
					                                                SettingAddon.formatDouble(
							                                                bank.getEconomy().getBalance()))));
					break;
				}
		} else help(commandSender, 1);
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		UserManager<Player> userManager = gangland.getInitializer().getUserManager();

		// create bank
		// glw bank create name
		Argument create = new Argument("create", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.isHasBank()) {
				player.sendMessage(MessageAddon.BANK_EXIST.toString());
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, getPermission() + ".create");

		HashMap<User<Player>, AtomicReference<String>> createBankName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>         createBankTimer = new HashMap<>();

		ConfirmArgument confirmCreate = new ConfirmArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.isHasBank()) {
				player.sendMessage(MessageAddon.BANK_EXIST.toString());
				return;
			}

			if (user.getEconomy().getBalance() < SettingAddon.getBankCreateFee()) {
				player.sendMessage(MessageAddon.CANNOT_CREATE_BANK.toString());
				return;
			}

			user.getEconomy().withdraw(SettingAddon.getBankCreateFee());
			user.setHasBank(true);

			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank bank) {
					bank.setName(createBankName.get(user).get());
					bank.getEconomy().setBalance(SettingAddon.getBankInitialBalance());
					break;
				}

			player.sendMessage(MessageAddon.BANK_CREATED.toString().replace("%bank%", createBankName.get(user).get()));

			createBankName.remove(user);

			CountdownTimer timer = createBankTimer.get(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				createBankTimer.remove(sender);
			}
		});

		create.addSubArgument(confirmCreate);

		Argument createName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.isHasBank()) {
				player.sendMessage(MessageAddon.BANK_EXIST.toString());
				return;
			}

			if (confirmCreate.isConfirmed()) return;

			createBankName.put(user, new AtomicReference<>(args[2]));

			// Need to notify the player and give access to confirm
			player.sendMessage(MessageAddon.BANK_CREATE_FEE.toString()
			                                               .replace("%amount%", SettingAddon.formatDouble(
					                                               SettingAddon.getBankCreateFee())));
			player.sendMessage(ChatUtil.confirmCommand(new String[]{"bank", "create"}));
			confirmCreate.setConfirmed(true);

			CountdownTimer timer = new CountdownTimer(gangland, 60 * 20L, time -> {
				sender.sendMessage(MessageAddon.BANK_CREATE_CONFIRM.toString()
				                                                   .replace("%timer%", TimeUtil.formatTime(
						                                                   time.getDuration() / 20L, true)));
			}, null, time -> {
				confirmCreate.setConfirmed(false);
				createBankName.remove(user);
			});

			timer.start();
			createBankTimer.put(sender, timer);
		});

		create.addSubArgument(createName);

		// delete bank
		// glw bank delete
		HashMap<User<Player>, AtomicReference<String>> deleteBankName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>         deleteBankTimer = new HashMap<>();

		ConfirmArgument confirmDelete = new ConfirmArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.isHasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK.toString());
				return;
			}

			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank bank) {
					user.getEconomy().deposit(bank.getEconomy().getBalance() + SettingAddon.getBankCreateFee() / 2);
					user.setHasBank(false);

					bank.setName("");
					bank.getEconomy().setBalance(0D);
					break;
				}

			player.sendMessage(MessageAddon.BANK_REMOVED.toString().replace("%bank%", deleteBankName.get(user).get()));

			deleteBankName.remove(user);

			CountdownTimer timer = deleteBankTimer.remove(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				deleteBankTimer.remove(sender);
			}
		});

		String[] delArr = {"delete", "remove"};
		Argument delete = new Argument(delArr, getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.isHasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK.toString());
				return;
			}

			if (confirmDelete.isConfirmed()) return;

			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank bank) {
					deleteBankName.put(user, new AtomicReference<>(bank.getName()));
					break;
				}

			player.sendMessage(ChatUtil.confirmCommand(new String[]{"bank", "delete"}));
			confirmDelete.setConfirmed(true);

			CountdownTimer timer = new CountdownTimer(gangland, 60 * 20L, time -> {
				sender.sendMessage(MessageAddon.BANK_REMOVE_CONFIRM.toString()
				                                                   .replace("%timer%", TimeUtil.formatTime(
						                                                   time.getDuration() / 20L, true)));
			}, null, time -> {
				confirmDelete.setConfirmed(false);
				deleteBankName.remove(user);
			});

			timer.start();
			deleteBankTimer.put(sender, timer);

		}, getPermission() + ".delete");

		delete.addSubArgument(confirmDelete);

		// deposit money to bank
		// glw bank deposit 10
		Argument deposit = new Argument(new String[]{"deposit", "add"}, getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.isHasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK.toString());
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>"));
		}, getPermission() + ".deposit");

		// withdraw money from a bank
		// glw bank withdraw 10
		String[] withdrawArr = {"withdraw", "take"};
		Argument withdraw = new Argument(withdrawArr, getArgumentTree(), (argument, sender, args) -> {
			Player player = (Player) sender;

			User<Player> user = userManager.getUser(player);
			if (!user.isHasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK.toString());
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>"));
		}, getPermission() + ".withdraw");

		Argument amount = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.isHasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK.toString());
				return;
			}

			try {
				double argAmount = Double.parseDouble(args[2]);
				String strArg    = "";

				Bank bank = null;
				for (Account<?, ?> account : user.getLinkedAccounts())
					if (account instanceof Bank) {
						bank = (Bank) account;
						break;
					}

				if (bank == null) throw new NullPointerException("Bank is null");

				switch (args[1].toLowerCase()) {
					case "deposit", "add" -> {
						strArg = "deposit";
						double inBank = bank.getEconomy().getBalance() + argAmount;

						if (inBank > SettingAddon.getBankMaxBalance()) {
							player.sendMessage(MessageAddon.CANNOT_EXCEED_MAXIMUM.toString());
							break;
						}

						processMoney(user, bank, user.getEconomy().getBalance(), argAmount, inBank,
						             user.getEconomy().getBalance() - argAmount);
					}
					case "withdraw", "take" -> {
						strArg = "withdraw";
						processMoney(user, bank, bank.getEconomy().getBalance(), argAmount,
						             bank.getEconomy().getBalance() - argAmount,
						             user.getEconomy().getBalance() + argAmount);
					}
				}

				user.getUser().sendMessage(MessageAddon.valueOf("BANK_MONEY_" + strArg.toUpperCase() + "_PLAYER")
				                                       .toString()
				                                       .replace("%amount%", SettingAddon.formatDouble(argAmount)));

			} catch (NumberFormatException exception) {
				player.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", args[2]));
			}
		});

		deposit.addSubArgument(amount);
		withdraw.addSubArgument(amount);

		// balance of gang
		// glw bank balance
		Argument balance = new Argument(new String[]{"balance", "bal"}, getArgumentTree(), (argument, sender, args) -> {
			Player player = (Player) sender;

			User<Player> user = userManager.getUser(player);
			if (!user.isHasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK.toString());
				return;
			}

			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank bank) {
					player.sendMessage(MessageAddon.BANK_BALANCE_PLAYER.toString()
					                                                   .replace("%balance%", SettingAddon.formatDouble(
							                                                   bank.getEconomy().getBalance())));
				}
		}, getPermission() + ".balance");

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(delete);
		arguments.add(deposit);
		arguments.add(withdraw);
		arguments.add(balance);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Bank");
	}

	private void processMoney(User<Player> user, Bank bank, double check, double amount, double inBank,
	                          double inAccount) {
		if (check == 0D) user.getUser().sendMessage(MessageAddon.CANNOT_TAKE_LESS_THAN_ZERO.toString());
		else if (amount > check) user.getUser().sendMessage(MessageAddon.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
		else {
			user.getEconomy().setBalance(inAccount);
			bank.getEconomy().setBalance(inBank);
		}
	}

}
