package me.luckyraven.command.sub.bank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.economy.bank.Bank;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

class BankResetCapCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final GanglandDatabase    ganglandDatabase;

	protected BankResetCapCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              UserManager<Player> userManager, GanglandDatabase ganglandDatabase) {
		super(gangland, "resetcap", tree, parent);

		this.gangland         = gangland;
		this.tree             = tree;
		this.userManager      = userManager;
		this.ganglandDatabase = ganglandDatabase;

		addSubArgument(playerArgument());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<player|all>"));
	}

	private OptionalArgument playerArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String target = args[2];

			if (target.equalsIgnoreCase("all")) {
				int touched = resetAll();
				sender.sendMessage(Messages.BANK_RESETCAP_ALL_SUCCESS.toString()
				                                                     .replace("%count%", String.valueOf(touched)));
				return;
			}

			Player       player = Bukkit.getPlayerExact(target);
			User<Player> user   = player == null ? null : userManager.getUser(player);
			if (user == null) {
				sender.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", target));
				return;
			}

			Bank bank = user.getBank();
			if (!user.hasBank() || bank == null) {
				sender.sendMessage(Messages.MUST_CREATE_BANK.toString());
				return;
			}

			resetAndSave(bank);

			sender.sendMessage(Messages.BANK_RESETCAP_SUCCESS.toString().replace("%player%", player.getName()));
		}, sender -> {
			List<String> suggestions = new ArrayList<>();
			suggestions.add("all");
			for (Player p : Bukkit.getOnlinePlayers()) suggestions.add(p.getName());
			return suggestions;
		});
	}

	private int resetAll() {
		IRepository<Bank> repo    = ganglandDatabase.getRepositoryRegistry().getRepository(Bank.class);
		int               touched = 0;
		for (Bank bank : repo.loadAll()) {
			resetCounters(bank);
			repo.save(bank);
			touched++;
		}
		// Online users hold in-memory Bank references; reset those too so the next banker interaction sees 0 / 0.
		for (Player online : Bukkit.getOnlinePlayers()) {
			User<Player> user = userManager.getUser(online);
			if (user != null && user.getBank() != null) resetCounters(user.getBank());
		}
		return touched;
	}

	private void resetAndSave(Bank bank) {
		resetCounters(bank);
		IRepository<Bank> repo = ganglandDatabase.getRepositoryRegistry().getRepository(Bank.class);
		repo.save(bank);
	}

	private void resetCounters(Bank bank) {
		bank.setDepositedToday(0D);
		bank.setWithdrawnToday(0D);
		bank.setCapResetAt(null);
	}

}
