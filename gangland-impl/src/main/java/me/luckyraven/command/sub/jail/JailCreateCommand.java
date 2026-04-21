package me.luckyraven.command.sub.jail;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.jail.Jail;
import me.luckyraven.copsncrooks.jail.JailRegistry;
import me.luckyraven.copsncrooks.jail.JailService;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class JailCreateCommand extends SubArgument {

	private final JailService  jailService;
	private final JailRegistry jailRegistry;

	protected JailCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            JailService jailService,
	                            JailRegistry jailRegistry) {
		super(gangland, "create", tree, parent);

		this.jailService  = jailService;
		this.jailRegistry = jailRegistry;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			Location location = player.getLocation();
			int      blocks   = 5;

			boolean checkForJail = jailRegistry.getCells()
					.stream().anyMatch(jail -> {
						Location jailLoc = jail.getLocation();
						if (jailLoc == null) return false;

						World jailLocWorld  = jailLoc.getWorld();
						World locationWorld = location.getWorld();
						if (jailLocWorld == null || !jailLocWorld.equals(locationWorld)) return false;
						return jailLoc.distanceSquared(location) < Math.pow(blocks, 2);
					});

			if (checkForJail) {
				sender.sendMessage(Messages.JAIL_EXISTS_NEARBY.toString()
				                                              .replace("%blocks%", String.valueOf(blocks)));
				return;
			}

			Jail jail = jailService.setJailLocation(location, Settings.getJailMaxCapacity());

			sender.sendMessage(Messages.JAIL_CREATED.toString()
			                                        .replace("%id%", String.valueOf(jail.getId())));
		};
	}
}
