package org.luckyraven.gangland.copsncrooks.command.jail;

import java.util.Map;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.integration.detainment.DetainmentSettings;
import org.luckyraven.gangland.copsncrooks.jail.Jail;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;

class JailCreateCommand extends SubArgument {

	private final JailService        jailService;
	private final JailRegistry       jailRegistry;
	private final DetainmentSettings detainmentSettings;
	private final CommandMessages commandMessages;

	protected JailCreateCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                            JailService jailService,
	                            JailRegistry jailRegistry,
	                            DetainmentSettings detainmentSettings,
	                            CommandMessages commandMessages) {
		super(plugin, "create", tree, parent);

		this.jailService        = jailService;
		this.jailRegistry       = jailRegistry;
		this.detainmentSettings = detainmentSettings;
		this.commandMessages    = commandMessages;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.NOT_PLAYER, Map.of()));
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
				sender.sendMessage(commandMessages.format(CommandMessages.Key.JAIL_EXISTS_NEARBY,
				                   Map.of("blocks", String.valueOf(blocks))));
				return;
			}

			Jail jail = jailService.setJailLocation(location, detainmentSettings.getJailMaxCapacity());

			sender.sendMessage(commandMessages.format(CommandMessages.Key.JAIL_CREATED,
				                   Map.of("id", String.valueOf(jail.getId()))));
		};
	}
}
