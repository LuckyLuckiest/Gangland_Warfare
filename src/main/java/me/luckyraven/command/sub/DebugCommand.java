package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.account.Account;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.bukkit.inventory.Inventory;
import me.luckyraven.bukkit.inventory.MultiInventory;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.rank.Rank;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class DebugCommand extends CommandHandler {

	public DebugCommand(Gangland gangland) {
		super(gangland, "debug", false);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		commandSender.sendMessage("Test");
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		// user data
		Argument userData = new Argument("user-data", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				UserManager<Player> userManager = gangland.getInitializer().getUserManager();

				User<Player> user = userManager.getUser(player);

				for (Account<?, ?> account : user.getLinkedAccounts())
					sender.sendMessage(account.toString());

				player.sendMessage(user.toString());
			} else {
				sender.sendMessage("Does nothing yet!");
			}
		});

		// gang data
		Argument gangData = new Argument("gang-data", getArgumentTree(), (argument, sender, args) -> {
			for (Gang gang : gangland.getInitializer().getGangManager().getGangs().values())
				sender.sendMessage(gang.toString());
		});

		// rank data
		Argument rankData = new Argument("rank-data", getArgumentTree(), (argument, sender, args) -> {
			for (Rank rank : gangland.getInitializer().getRankManager().getRanks().values())
				sender.sendMessage(rank.toString());
		});

		// multi inventory
		Argument multiInv = new Argument("multi-inv", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				MultiInventory mainInventory = new MultiInventory(gangland, "Main Inventory", Inventory.MAX_SLOTS);
				Inventory      inventory1    = new Inventory(gangland, "Inventory 1", Inventory.MAX_SLOTS);
				Inventory      inventory2    = new Inventory(gangland, "Inventory 2", Inventory.MAX_SLOTS);
				Inventory      inventory3    = new Inventory(gangland, "Inventory 3", Inventory.MAX_SLOTS);
				Inventory      inventory4    = new Inventory(gangland, "Inventory 4", Inventory.MAX_SLOTS);

				mainInventory.addPage(player, inventory1);
				mainInventory.addPage(player, inventory2);
				mainInventory.addPage(player, inventory3);
				mainInventory.addPage(player, inventory4);

				for (Inventory inv : mainInventory.getInventories())
					inv.fillInventory();

				mainInventory.removePage(inventory2);

				mainInventory.open(player);
			} else {
				sender.sendMessage("How will you see the inventory?");
			}
		});

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(userData);
		arguments.add(gangData);
		arguments.add(rankData);
		arguments.add(multiInv);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	public void help(CommandSender sender, int page) {

	}

}
