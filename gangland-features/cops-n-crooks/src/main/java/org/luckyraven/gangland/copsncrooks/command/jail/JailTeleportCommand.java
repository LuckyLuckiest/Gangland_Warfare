package org.luckyraven.gangland.copsncrooks.command.jail;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.Map;

class JailTeleportCommand extends SubArgument {

	private final JavaPlugin       plugin;
	private final Tree<Argument> tree;
	private final JailRegistry   jailRegistry;
	private final CommandMessages commandMessages;

	JailTeleportCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, JailRegistry jailRegistry,
	                    CommandMessages commandMessages) {
		super(plugin, new String[]{"teleport", "tp"}, tree, parent);

		this.plugin          = plugin;
		this.tree            = tree;
		this.jailRegistry    = jailRegistry;
		this.commandMessages = commandMessages;

		this.idArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(commandMessages.usage("/glw jail teleport <id>"));
		};
	}

	private void idArgument() {
		Argument idArg = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.NOT_PLAYER, Map.of()));
				return;
			}

			String idStr = args[2];
			int    id;
			try {
				id = Integer.parseInt(idStr);
			} catch (NumberFormatException e) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.BAD_ID, Map.of("value", idStr)));
				return;
			}

			Location location = jailRegistry.getJailLocation(id);

			if (location == null) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.JAIL_UNKNOWN, Map.of("id", idStr)));
				return;
			}

			player.teleport(location);
			sender.sendMessage(commandMessages.format(CommandMessages.Key.JAIL_TELEPORTED, Map.of("id", String.valueOf(id))));
		}, sender -> {
			return jailRegistry.getCells()
					.stream().map(jail -> String.valueOf(jail.getId())).toList();
		});

		this.addSubArgument(idArg);
	}
}
