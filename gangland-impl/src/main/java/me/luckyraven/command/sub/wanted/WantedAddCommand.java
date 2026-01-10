package me.luckyraven.command.sub.wanted;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.wanted.Wanted;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

class WantedAddCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	public WantedAddCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "add", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager = gangland.getInitializer().getUserManager();

		wantedValue();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Wanted       wanted = user.getWanted();

			int amount = 1;
			if (wanted.getLevel() + amount > wanted.getMaxLevel()) amount = 0;
			wanted.setLevel(Math.min(wanted.getMaxLevel(), wanted.getLevel() + amount));

			String increased = MessageAddon.WANTED_INCREASED.toString();
			String replace = increased.replace("%amount%", String.valueOf(amount))
									  .replace("%stars%", wanted.getLevelStars());

			sender.sendMessage(replace);
		};
	}

	private void wantedValue() {
		Argument amountValue = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String amountStr = args[2];

			int amount;
			try {
				amount = Integer.parseInt(amountStr);
			} catch (NumberFormatException exception) {
				sender.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", amountStr));
				return;
			}

			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Wanted       wanted = user.getWanted();

			int realAmount = amount;
			if (wanted.getLevel() + realAmount > wanted.getMaxLevel()) realAmount = wanted.getMaxLevel() -
																					wanted.getLevel();
			int value = Math.min(wanted.getMaxLevel(), wanted.getLevel() + amount);
			wanted.setLevel(value);

			String increased = MessageAddon.WANTED_INCREASED.toString();
			String replace = increased.replace("%amount%", String.valueOf(realAmount))
									  .replace("%stars%", wanted.getLevelStars());

			sender.sendMessage(replace);
		}, sender -> List.of("<amount>"));

		this.addSubArgument(amountValue);
	}

}
