package org.luckyraven.gangland.copsncrooks.command.jail;

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
import org.luckyraven.gangland.copsncrooks.jail.Jail;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.Map;

class JailInfoCommand extends SubArgument {

	private final JavaPlugin       plugin;
	private final Tree<Argument>   tree;
	private final JailRegistry     jailRegistry;
	private final CommandMessages  messages;

	protected JailInfoCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, JailRegistry jailRegistry,
	                          CommandMessages messages) {
		super(plugin, "info", tree, parent);

		this.plugin       = plugin;
		this.tree         = tree;
		this.jailRegistry = jailRegistry;
		this.messages     = messages;

		this.idArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(messages.usage("/glw jail info <id>"));
		};
	}

	private void idArgument() {
		Argument idArg = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			String idStr = args[2];
			int    id;
			try {
				id = Integer.parseInt(idStr);
			} catch (NumberFormatException e) {
				sender.sendMessage(messages.format(CommandMessages.Key.BAD_ID, Map.of("value", idStr)));
				return;
			}

			Jail jail = jailRegistry.getJail(id);

			if (jail == null) {
				sender.sendMessage(messages.format(CommandMessages.Key.JAIL_UNKNOWN, Map.of("id", idStr)));
				return;
			}

			String tpCommand = String.format("/%s jail teleport %d", GanglandApi.SHORT_PREFIX, id);
			String header    = messages.format(CommandMessages.Key.JAIL_INFO_HEADER, Map.of("id", String.valueOf(id)));

			String tp        = messages.format(CommandMessages.Key.JAIL_INFO_TP, Map.of());

			var message = new ComponentBuilder(header)
					.append(tp)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
					.create();

			sender.spigot().sendMessage(message);

			String capacity = jail.getJailedPlayersId().size() + "/" + jail.getMaxCapacity();
			sender.sendMessage(messages.format(CommandMessages.Key.JAIL_INFO_LINE,
			                                   location(jail.getLocation(), capacity)));
		}, sender -> {
			return jailRegistry.getCells()
					.stream().map(jail -> String.valueOf(jail.getId())).toList();
		});

		this.addSubArgument(idArg);
	}

	private static Map<String, String> location(Location location, String capacity) {
		String world = location.getWorld() != null ? location.getWorld().getName() : "?";

		return Map.of("world", world,
		              "x", String.valueOf(location.getBlockX()),
		              "y", String.valueOf(location.getBlockY()),
		              "z", String.valueOf(location.getBlockZ()),
		              "capacity", capacity);
	}
}
