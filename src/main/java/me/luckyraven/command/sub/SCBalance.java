package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.account.type.Player;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.data.user.User;
import me.luckyraven.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public final class SCBalance extends CommandHandler {

	public SCBalance(Gangland gangland) {
		super(gangland, "balance", true, "bal");
		getHelpInfo().add(getCommandInformation("balance"));
		getHelpInfo().add(getCommandInformation("balance_others"));
	}

	@Override
	public void onExecute(CommandSender sender, Command command, String[] args) {
		// Initialize the player as the sender, already checked if it was a bukkit player
		org.bukkit.entity.Player pl = (org.bukkit.entity.Player) sender;
		// Initialize a user
		User<org.bukkit.entity.Player> user = new User<>(pl);
		// Initialize a player with their account
		Player player = new Player(pl.getUniqueId(), user);
		// Optional to call economy
		Economy economy = new Economy(user);

		player.sendMessage("&bBalance&7: &a$" + economy.getBalance());
	}

	@Override
	public void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Balance");
	}

}
