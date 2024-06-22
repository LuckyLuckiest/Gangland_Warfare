package me.luckyraven.command.sub.bank;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.data.account.type.Bank;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.tables.BankTable;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TimeUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.timer.CountdownTimer;
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
		super(new String[]{"delete", "remove", "del"}, tree, parent);

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
			Bank         bank   = Bank.getInstance(user);

			if (!user.hasBank() || bank == null) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK.toString());
				return;
			}

			if (confirmDelete.isConfirmed()) return;

			deleteBankName.put(user, new AtomicReference<>(bank.getName()));
			confirmDelete.setConfirmed(true);

			CountdownTimer timer = getCountdownTimer(sender, player, user);
			deleteBankTimer.put(sender, timer);
		};
	}

	@NotNull
	private CountdownTimer getCountdownTimer(CommandSender sender, Player player, User<Player> user) {
		CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
			player.sendMessage(ChatUtil.confirmCommand(new String[]{"bank", "delete"}));
		}, time -> {
			if (time.getTimeLeft() % 20 != 0) return;

			sender.sendMessage(MessageAddon.BANK_REMOVE_CONFIRM.toString()
															   .replace("%timer%",
																		TimeUtil.formatTime(time.getTimeLeft(), true)));
		}, time -> {
			confirmDelete.setConfirmed(false);
			deleteBankName.remove(user);
		});

		timer.start(false);
		return timer;
	}

	private ConfirmArgument bankDeleteConfirm() {
		return new ConfirmArgument(tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Bank         bank   = Bank.getInstance(user);

			if (!user.hasBank() || bank == null) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK.toString());
				return;
			}

			user.getEconomy().deposit(bank.getEconomy().getBalance() + SettingAddon.getBankCreateFee() / 2);
			user.setHasBank(false);
			user.removeAccount(bank);

			// remove the bank from the database
			Initializer      initializer      = gangland.getInitializer();
			GanglandDatabase ganglandDatabase = initializer.getGanglandDatabase();
			DatabaseHelper   helper           = new DatabaseHelper(gangland, ganglandDatabase);

			BankTable bankTable = initializer.getInstanceFromTables(BankTable.class, ganglandDatabase.getTables());

			helper.runQueriesAsync(database -> {
				database.table(bankTable.getName()).delete("uuid = ?", user.getUser().getUniqueId().toString());
			});

			player.sendMessage(MessageAddon.BANK_REMOVED.toString().replace("%bank%", deleteBankName.get(user).get()));

			deleteBankName.remove(user);

			CountdownTimer timer = deleteBankTimer.remove(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				deleteBankTimer.remove(sender);
			}
		});
	}
}
