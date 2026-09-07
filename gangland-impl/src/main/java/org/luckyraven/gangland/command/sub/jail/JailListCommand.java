package org.luckyraven.gangland.command.sub.jail;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

class JailListCommand extends SubArgument {

	private final JailRegistry jailRegistry;

	protected JailListCommand(Gangland gangland, Tree<Argument> tree, Argument parent, JailRegistry jailRegistry) {
		super(gangland, "list", tree, parent);

		this.jailRegistry = jailRegistry;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(Messages.JAIL_LIST_HEADER.toString());
			jailRegistry.getCells().forEach(jail -> {
				Location location = jail.getLocation();
				int      x        = location.getBlockX();
				int      y        = location.getBlockY();
				int      z        = location.getBlockZ();
				String   world    = location.getWorld() != null ? location.getWorld().getName() : "?";

				int    id        = jail.getId();
				String tpCommand = String.format("/%s jail teleport %d", Gangland.SHORT_PREFIX, id);
				String hoverText = String.format("%s - %d, %d, %d", world, x, y, z);

				var message = new ComponentBuilder(GanglandChatUtil.color(" &b- &7" + id + " "))
						.append(GanglandChatUtil.color("&e(&btp&e)"))
						.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
						.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverText)))
						.create();

				sender.spigot().sendMessage(message);
			});
		};
	}
}
