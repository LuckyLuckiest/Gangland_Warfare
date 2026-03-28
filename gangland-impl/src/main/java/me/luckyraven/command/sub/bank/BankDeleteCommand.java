package me.luckyraven.command.sub.bank;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.data.account.Bank;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TimeMessages;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.util.timer.CountdownTimer;
import me.luckyraven.util.utilities.TimeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class BankDeleteCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	private final Map<User<Player>, AtomicReference<String>> deleteBankName;
	private final Map<CommandSender, CountdownTimer>         deleteBankTimer;

	private final ConfirmArgument confirmDelete;

	protected BankDeleteCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, new String[]{"delete", "remove", "del"}, tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager     = gangland.getInitializer().getUserManager();
		this.deleteBankName  = new HashMap<>();
		this.deleteBankTimer = new HashMap<>();

		this.confirmDelete = bankDeleteConfirm();
		this.addSubArgument(confirmDelete);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Bank bank = user.getBank();

			if (!user.hasBank() || bank == null) {
				user.sendMessage(Messages.MUST_CREATE_BANK.toString());
				return;
			}

			if (confirmDelete.isLocked(sender)) return;

			deleteBankName.put(user, new AtomicReference<>(bank.getName()));

			confirmDelete.lock(sender, s -> deleteBankTimer.put(s, getCountdownTimer(s, user)));
		};
	}

	@NotNull
	private CountdownTimer getCountdownTimer(CommandSender sender, User<Player> user) {
		CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
			user.sendMessage(GanglandChatUtil.confirmCommand(new String[]{"bank", "delete"}));
		}, time -> {
			if (time.getTimeLeft() % 20 != 0) return;

			String string = Messages.BANK_REMOVE_CONFIRM.toString();
			String replace = string.replace("%timer%",
			                                TimeUtil.formatTime(time.getTimeLeft(), true, TimeMessages.getInstance()));
			sender.sendMessage(replace);
		}, time -> {
			confirmDelete.unlock(sender);
			deleteBankName.remove(user);
		});

		timer.start(false);
		return timer;
	}

	private ConfirmArgument bankDeleteConfirm() {
		return new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Bank bank = user.getBank();

			if (!user.hasBank() || bank == null) {
				user.sendMessage(Messages.MUST_CREATE_BANK.toString());
				return;
			}

			user.getEconomy().deposit(bank.getEconomy().getBalance() + Settings.getBankCreateFee() / 2);
			user.setBank(null);

			// remove the bank from the database
			Initializer       initializer      = gangland.getInitializer();
			GanglandDatabase  ganglandDatabase = initializer.getGanglandDatabase();
			IRepository<Bank> bankRepository   = ganglandDatabase.getRepositoryRegistry().getRepository(Bank.class);

			bankRepository.delete(bank);

			String string  = Messages.BANK_REMOVED.toString();
			String replace = string.replace("%bank%", deleteBankName.get(user).get());

			user.sendMessage(replace);

			deleteBankName.remove(user);

			CountdownTimer timer = deleteBankTimer.remove(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				deleteBankTimer.remove(sender);
			}
		});
	}
}
