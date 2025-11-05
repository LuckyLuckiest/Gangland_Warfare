package me.luckyraven.command.sub.bank;

import me.luckyraven.Gangland;
import me.luckyraven.TriConsumer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.type.Bank;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.timer.CountdownTimer;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TimeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

class BankCreateCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	protected BankCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "create", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager = gangland.getInitializer().getUserManager();

		bankCreate();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.hasBank()) {
				player.sendMessage(MessageAddon.BANK_EXIST.toString());
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void bankCreate() {
		HashMap<User<Player>, AtomicReference<String>> createBankName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>         createBankTimer = new HashMap<>();

		ConfirmArgument confirmCreate = new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.hasBank()) {
				player.sendMessage(MessageAddon.BANK_EXIST.toString());
				return;
			}

			if (user.getEconomy().getBalance() < SettingAddon.getBankCreateFee()) {
				player.sendMessage(MessageAddon.CANNOT_CREATE_BANK.toString());
				return;
			}

			Bank bank = new Bank(user, createBankName.get(user).get());

			// create the bank
			user.getEconomy().withdraw(SettingAddon.getBankCreateFee());
			user.setHasBank(true);
			user.addAccount(bank);
			bank.getEconomy().setBalance(SettingAddon.getBankInitialBalance());

			player.sendMessage(MessageAddon.BANK_CREATED.toString().replace("%bank%", createBankName.get(user).get()));

			createBankName.remove(user);

			CountdownTimer timer = createBankTimer.get(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				createBankTimer.remove(sender);
			}
		});

		this.addSubArgument(confirmCreate);

		Argument createName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.hasBank()) {
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

			CountdownTimer timer = new CountdownTimer(gangland, 60, null, time -> {
				if (time.getTimeLeft() % 20 != 0) return;

				sender.sendMessage(MessageAddon.BANK_CREATE_CONFIRM.toString()
																   .replace("%timer%",
																			TimeUtil.formatTime(time.getTimeLeft(),
																								true)));
			}, time -> {
				confirmCreate.setConfirmed(false);
				createBankName.remove(user);
			});

			timer.start(false);
			createBankTimer.put(sender, timer);
		});

		this.addSubArgument(createName);
	}
}
