package org.luckyraven.gangland.copsncrooks.command.cops.spawner;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.GanglandApi;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.Map;

class CopSpawnerInfoCommand extends SubArgument {

	private final JavaPlugin        plugin;
	private final Tree<Argument>    tree;
	private final CopSpawnManager   copSpawnManager;
	private final CommandMessages   messages;

	CopSpawnerInfoCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, CopSpawnManager copSpawnManager,
	                      CommandMessages messages) {
		super(plugin, "info", tree, parent);

		this.plugin          = plugin;
		this.tree            = tree;
		this.copSpawnManager = copSpawnManager;
		this.messages        = messages;

		this.idArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(messages.usage("/glw cop spawner info <id>"));
		};
	}

	private void idArgument() {
		Argument idArg = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			String idStr = args[3];
			int    id;
			try {
				id = Integer.parseInt(idStr);
			} catch (NumberFormatException e) {
				sender.sendMessage(messages.format(CommandMessages.Key.BAD_ID, Map.of("value", idStr)));
				return;
			}

			Location location = copSpawnManager.getSpawnerLocation(id);

			if (location == null) {
				sender.sendMessage(messages.format(CommandMessages.Key.SPAWNER_UNKNOWN, Map.of("id", idStr)));
				return;
			}

			String tpCommand = String.format("/%s cop spawner teleport %d", GanglandApi.SHORT_PREFIX, id);
			String header    = messages.format(CommandMessages.Key.SPAWNER_INFO_HEADER,
			                                   Map.of("id", String.valueOf(id)));

			String tp        = messages.format(CommandMessages.Key.SPAWNER_INFO_TP, Map.of());

			var message = new ComponentBuilder(header)
					.append(tp)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
					.create();

			sender.spigot().sendMessage(message);

			sender.sendMessage(messages.format(CommandMessages.Key.SPAWNER_INFO_LINE, location(location)));
		}, sender -> copSpawnManager.getSpawnerIds()
				.stream().map(String::valueOf).toList());

		this.addSubArgument(idArg);
	}

	private static Map<String, String> location(Location location) {
		String world = location.getWorld() != null ? location.getWorld().getName() : "?";

		return Map.of("world", world,
		              "x", String.valueOf(location.getBlockX()),
		              "y", String.valueOf(location.getBlockY()),
		              "z", String.valueOf(location.getBlockZ()));
	}
}
