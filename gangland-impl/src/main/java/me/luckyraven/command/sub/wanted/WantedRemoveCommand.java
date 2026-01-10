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

class WantedRemoveCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	public WantedRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "remove", tree, parent);

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
			if (wanted.getLevel() - amount < 0) amount = 0;
			wanted.setLevel(Math.max(0, wanted.getLevel() - amount));

			String decreased = MessageAddon.WANTED_DECREASED.toString();
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
				sender.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", amountStr));
				return;
			}

			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Wanted       wanted = user.getWanted();

			int realAmount = amount;
			if (wanted.getLevel() - realAmount < 0) realAmount = wanted.getLevel();
			int value = Math.max(0, wanted.getLevel() - amount);
			wanted.setLevel(value);

			String decreased = MessageAddon.WANTED_DECREASED.toString();
			String replace = decreased.replace("%amount%", String.valueOf(realAmount))
									  .replace("%stars%", wanted.getLevelStars());

			sender.sendMessage(replace);
		}, sender -> List.of("<amount>"));

		this.addSubArgument(amountValue);
	}

}
