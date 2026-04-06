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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class JailCreateCommand extends SubArgument {

	private final Gangland gangland;

	protected JailCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
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
			Location    location    = player.getLocation();
			int         blocks      = 5;

			boolean checkForJail = jailService.getJailRegistry().getCells()
					.stream().anyMatch(jail -> {
						Location jailLoc = jail.getLocation();
						if (jailLoc == null) return false;

						World jailLocWorld  = jailLoc.getWorld();
						World locationWorld = location.getWorld();
						if (jailLocWorld == null || !jailLocWorld.equals(locationWorld)) return false;
						return jailLoc.distanceSquared(location) < Math.pow(blocks, 2);
					});

			if (checkForJail) {
				sender.sendMessage(GanglandChatUtil.errorMessage(
						"A jail already exists within " + blocks + " blocks of this location!"));
				return;
			}

			Jail jail = jailService.setJailLocation(location, Settings.getJailMaxCapacity());

			sender.sendMessage(
					GanglandChatUtil.commandMessage("&aJail &e" + jail.getId() + "&a created at your location."));
		};
	}
}
