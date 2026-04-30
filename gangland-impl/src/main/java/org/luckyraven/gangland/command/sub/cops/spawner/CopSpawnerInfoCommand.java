package org.luckyraven.gangland.command.sub.cops.spawner;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

class CopSpawnerInfoCommand extends SubArgument {

	private final Gangland        gangland;
	private final Tree<Argument>  tree;
	private final CopSpawnManager copSpawnManager;

	CopSpawnerInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent, CopSpawnManager copSpawnManager) {
		super(gangland, "info", tree, parent);

		this.gangland        = gangland;
		this.tree            = tree;
		this.copSpawnManager = copSpawnManager;

		this.idArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<id>"));
		};
	}

	private void idArgument() {
		Argument idArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String idStr = args[3];
			int    id;
			try {
				id = Integer.parseInt(idStr);
			} catch (NumberFormatException e) {
				sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", idStr));
				return;
			}

			Location location = copSpawnManager.getSpawnerLocation(id);

			if (location == null) {
				sender.sendMessage(Messages.LOCATION_NOT_FOUND.toString().replace("%location%", idStr));
				return;
			}

			int    x     = location.getBlockX();
			int    y     = location.getBlockY();
			int    z     = location.getBlockZ();
			String world = location.getWorld() != null ? location.getWorld().getName() : "?";

			String tpCommand = String.format("/%s cop spawner teleport %d", Gangland.SHORT_PREFIX, id);

			String color = GanglandChatUtil.color("&7&lCop spawner &e(&b" + id + "&e)&7: ");
			var message = new ComponentBuilder(color).append(GanglandChatUtil.color("&e(&btp&e)"))
			                                         .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
			                                         .create();

			sender.spigot().sendMessage(message);

			String info = String.format(" &bX: &7%d\n &bY: &7%d\n &bZ: &7%d\n &bWorld: &7%s", x, y, z, world);

			sender.sendMessage(GanglandChatUtil.color(info));
		}, sender -> copSpawnManager.getSpawnerIds()
				.stream().map(String::valueOf).toList());

		this.addSubArgument(idArg);
	}
}
