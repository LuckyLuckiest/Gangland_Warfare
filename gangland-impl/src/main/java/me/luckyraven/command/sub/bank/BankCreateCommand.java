package me.luckyraven.command.sub.bank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.Bank;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TimeMessages;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.util.timer.CountdownTimer;
import me.luckyraven.util.utilities.TimeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
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

			if (user == null) return;

			if (user.hasBank()) {
				user.sendMessage(Messages.BANK_EXIST.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void bankCreate() {
		HashMap<User<Player>, AtomicReference<String>> createBankName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>         createBankTimer = new HashMap<>();

		ConfirmArgument confirmCreate = new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (user.hasBank()) {
				user.sendMessage(Messages.BANK_EXIST.toString());
				return;
			}

			if (user.getEconomy().getBalance() < Settings.getBankCreateFee()) {
				user.sendMessage(Messages.CANNOT_CREATE_BANK.toString());
				return;
			}

			Bank bank = new Bank(user.getUser().getUniqueId(), createBankName.get(user).get());
			bank.getEconomy().setUser(user);

			// create the bank
			user.getEconomy().withdraw(Settings.getBankCreateFee());
			bank.getEconomy().setBalance(Settings.getBankInitialBalance());

			user.setBank(bank);

			String string  = Messages.BANK_CREATED.toString();
			String replace = string.replace("%bank%", createBankName.get(user).get());

			user.sendMessage(replace);

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

			if (user == null) return;

			if (user.hasBank()) {
				user.sendMessage(Messages.BANK_EXIST.toString());
				return;
			}

			if (confirmCreate.isLocked(sender)) return;

			createBankName.put(user, new AtomicReference<>(args[2]));

			// Need to notify the player and give access to confirm
			String string  = Messages.BANK_CREATE_FEE.toString();
			String replace = string.replace("%amount%", Settings.formatDouble(Settings.getBankCreateFee()));

			user.sendMessage(replace);
			user.sendMessage(GanglandChatUtil.confirmCommand(new String[]{"bank", "create"}));

			confirmCreate.lock(sender, s -> {
				CountdownTimer timer = new CountdownTimer(gangland, 60, null, time -> {
					if (time.getTimeLeft() % 20 != 0) return;

					String string1 = Messages.BANK_CREATE_CONFIRM.toString();
					String replace1 = string1.replace("%timer%", TimeUtil.formatTime(time.getTimeLeft(), true,
					                                                                 TimeMessages.getInstance()));
					s.sendMessage(replace1);
				}, time -> {
					confirmCreate.unlock(s);
					createBankName.remove(user);
				});

				timer.start(false);
				createBankTimer.put(s, timer);
			});
		}, sender -> List.of("<name>"));

		this.addSubArgument(createName);
	}
}
