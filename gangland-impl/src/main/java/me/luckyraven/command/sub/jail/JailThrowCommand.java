package me.luckyraven.command.sub.jail;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.detainment.DetainmentRegistry;
import me.luckyraven.copsncrooks.jail.Jail;
import me.luckyraven.copsncrooks.jail.JailService;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class JailThrowCommand extends SubArgument {

	private final Gangland gangland;

	JailThrowCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "throw", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			String playerStr = args[2];
			Player target    = Bukkit.getPlayer(playerStr);

			if (target == null) {
				sender.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", playerStr));
				return;
			}

			// find an empty jail and throw the player there
			DetainmentRegistry detainmentRegistry = gangland.getInitializer().getDetainmentRegistry();
			JailService        jailService        = gangland.getInitializer().getJailService();

			Jail jail = detainmentRegistry.findEmptyJail();

			if (jail == null) {
				sender.sendMessage(GanglandChatUtil.errorMessage("No empty jail found!"));
				return;
			}

			jailService.detainPlayer(jail.getId(), target.getUniqueId());
			sender.sendMessage(GanglandChatUtil.commandMessage("&aThrown &e" + target.getName() + "&a to jail."));
		};
	}
}
