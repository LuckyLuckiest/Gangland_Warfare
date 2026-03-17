package me.luckyraven.command.sub.copsncrooks;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.jail.JailManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class JailCreateCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	JailCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "create", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		idArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				ChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<id>"));
	}

	private void idArgument() {
		Argument idArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(ChatUtil.commandMessage("&cOnly players can use this command."));
				return;
			}

			String idStr = args[2];
			int    id;
			try {
				id = Integer.parseInt(idStr);
			} catch (NumberFormatException e) {
				sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", idStr));
				return;
			}

			JailManager jailManager = gangland.getInitializer().getJailManager();
			jailManager.setJailLocation(id, player.getLocation());

			sender.sendMessage(ChatUtil.commandMessage("&aJail &e" + id + "&a created at your location."));
		}, sender -> gangland.getInitializer().getJailManager().getJailService().getCells()
				.stream().map(jail -> String.valueOf(jail.getId())).toList());

		this.addSubArgument(idArg);
	}
}
