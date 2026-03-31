package me.luckyraven.command.sub.cops;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawnManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

class CopSpawnerInfoCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	CopSpawnerInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "info", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

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

			CopSpawnManager copSpawnManager = gangland.getInitializer().getCopSpawnManager();
			Location        location        = copSpawnManager.getSpawnerLocation(id);

			if (location == null) {
				sender.sendMessage(Messages.LOCATION_NOT_FOUND.toString().replace("%location%", idStr));
				return;
			}

			int    x     = location.getBlockX();
			int    y     = location.getBlockY();
			int    z     = location.getBlockZ();
			String world = location.getWorld() != null ? location.getWorld().getName() : "?";

			String tpCommand = String.format("/%s cop spawner teleport %d", Gangland.SHORT_PREFIX, id);

			var message = new ComponentBuilder(GanglandChatUtil.color("&7&lCop spawner &e(&b" + id + "&e)&7: "))
					.append(GanglandChatUtil.color("&e(&btp&e)"))
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
					.create();

			sender.spigot().sendMessage(message);

			String info = String.format(" &bX: &7%d\n &bY: &7%d\n &bZ: &7%d\n &bWorld: &7%s", x, y, z, world);

			sender.sendMessage(GanglandChatUtil.color(info));
		}, sender -> gangland.getInitializer().getCopSpawnManager().getSpawnerIds()
				.stream().map(String::valueOf).toList());

		this.addSubArgument(idArg);
	}
}
