package org.luckyraven.gangland.command.sub.wanted;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.gang.wanted.Wanted;

import java.util.List;

class WantedRemoveCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	public WantedRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                           UserManager<Player> userManager) {
		super(gangland, "remove", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager = userManager;

		wantedValue();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Wanted wanted = user.getWanted();

			int amount = 1;
			if (wanted.getLevel() - amount < 0) amount = 0;
			wanted.setLevel(Math.max(0, wanted.getLevel() - amount));

			String decreased = Messages.WANTED_DECREASED.toString();
			String replace = decreased.replace("%amount%", String.valueOf(amount))
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
				sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", amountStr));
				return;
			}

			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Wanted wanted = user.getWanted();

			int realAmount = amount;
			if (wanted.getLevel() - realAmount < 0) realAmount = wanted.getLevel();
			int value = Math.max(0, wanted.getLevel() - amount);
			wanted.setLevel(value);

			String decreased = Messages.WANTED_DECREASED.toString();
			String replace = decreased.replace("%amount%", String.valueOf(realAmount))
			                          .replace("%stars%", wanted.getLevelStars());

			sender.sendMessage(replace);
		}, sender -> List.of("<amount>"));

		this.addSubArgument(amountValue);
	}

}
