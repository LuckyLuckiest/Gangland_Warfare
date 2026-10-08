package org.luckyraven.gangland.copsncrooks.command.jail;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.GanglandApi;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.Map;

class JailListCommand extends SubArgument {

	private final JailRegistry    jailRegistry;
	private final CommandMessages messages;

	protected JailListCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, JailRegistry jailRegistry,
	                          CommandMessages messages) {
		super(plugin, "list", tree, parent);

		this.jailRegistry = jailRegistry;
		this.messages     = messages;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			var cells = jailRegistry.getCells();

			if (cells.isEmpty()) {
				sender.sendMessage(messages.format(CommandMessages.Key.JAIL_LIST_EMPTY, Map.of()));
				return;
			}

			sender.sendMessage(messages.format(CommandMessages.Key.JAIL_LIST_HEADER,
			                                   Map.of("count", String.valueOf(cells.size()))));
			cells.forEach(jail -> {
				int    id        = jail.getId();
				String tpCommand = String.format("/%s jail teleport %d", GanglandApi.SHORT_PREFIX, id);
				String hoverText = messages.format(CommandMessages.Key.JAIL_LIST_HOVER,
				                                   location(jail.getLocation()));
				String row       = messages.format(CommandMessages.Key.JAIL_LIST_ROW,
				                                   Map.of("id", String.valueOf(id)));

				var message = new ComponentBuilder(row)
						.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
						.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverText)))
						.create();

				sender.spigot().sendMessage(message);
			});
		};
	}

	private static Map<String, String> location(Location location) {
		String world = location.getWorld() != null ? location.getWorld().getName() : "?";

		return Map.of("world", world,
		              "x", String.valueOf(location.getBlockX()),
		              "y", String.valueOf(location.getBlockY()),
		              "z", String.valueOf(location.getBlockZ()));
	}
}
