package me.luckyraven.command.sub.wanted;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.feature.wanted.Wanted;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WantedRemoveCommand extends SubArgument {

	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	public WantedRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super("remove", tree, parent);

		this.tree = tree;

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

			sender.sendMessage(String.format("Removed %d wanted level%s.", amount, ChatUtil.plural(amount)),
			                   wanted.getLevelStr());
		};
	}

	private void wantedValue() {
		Argument optional = new OptionalArgument(tree, (argument, sender, args) -> {
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

			sender.sendMessage(String.format("Removed %d wanted level%s.", realAmount, ChatUtil.plural(realAmount)),
			                   wanted.getLevelStr());
		});

		this.addSubArgument(optional);
	}

}
