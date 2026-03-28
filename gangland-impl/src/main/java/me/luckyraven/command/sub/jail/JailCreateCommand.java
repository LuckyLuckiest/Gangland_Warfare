package me.luckyraven.command.sub.jail;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.jail.Jail;
import me.luckyraven.copsncrooks.jail.JailService;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class JailCreateCommand extends SubArgument {

	private final Gangland gangland;

	JailCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "create", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			JailService jailService = gangland.getInitializer().getJailService();
			Jail jail = jailService.setJailLocation(player.getLocation(),
			                                        Settings.getJailMaxCapacity());

			sender.sendMessage(
					GanglandChatUtil.commandMessage("&aJail &e" + jail.getId() + "&a created at your location."));
		};
	}
}
