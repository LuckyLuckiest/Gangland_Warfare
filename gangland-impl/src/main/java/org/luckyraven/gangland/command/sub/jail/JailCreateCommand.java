package org.luckyraven.gangland.command.sub.jail;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.jail.Jail;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;

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
