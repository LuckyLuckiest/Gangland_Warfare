package org.luckyraven.gangland.command.sub.cops.spawner;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

class CopSpawnerListCommand extends SubArgument {

	private final CopSpawnManager copSpawnManager;

	CopSpawnerListCommand(Gangland gangland, Tree<Argument> tree, Argument parent, CopSpawnManager copSpawnManager) {
		super(gangland, "list", tree, parent);
		this.copSpawnManager = copSpawnManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, strings) -> {
			sender.sendMessage(Messages.COP_SPAWNER_LIST_HEADER.toString());
			copSpawnManager.getSpawners().forEach(spawner -> {
				Location location = spawner.getLocation();
				int      x        = location.getBlockX();
				int      y        = location.getBlockY();
				int      z        = location.getBlockZ();
				String   world    = location.getWorld() != null ? location.getWorld().getName() : "?";

				int    id        = spawner.getId();
				String tpCommand = String.format("/%s cop spawner teleport %d", Gangland.SHORT_PREFIX, id);
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
