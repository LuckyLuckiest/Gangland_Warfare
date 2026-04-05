package me.luckyraven.command.sub.civilians;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

class CivilianSpawnerListCommand extends SubArgument {

	private final Gangland gangland;

	CivilianSpawnerListCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "list", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, strings) -> {
			CivilianSpawnManager spawnManager = gangland.getInitializer().getCivilianSpawnManager();

			sender.sendMessage(GanglandChatUtil.prefixMessage("Civilian spawners:"));
			spawnManager.getSpawners().forEach(spawner -> {
				Location location = spawner.getLocation();
				int      x        = location.getBlockX();
				int      y        = location.getBlockY();
				int      z        = location.getBlockZ();
				String   world    = location.getWorld() != null ? location.getWorld().getName() : "?";

				int    id        = spawner.getId();
				String typeId    = spawner.getTypeId() != null ? spawner.getTypeId() : "any";
				String tpCommand = String.format("/%s civilian spawner teleport %d", Gangland.SHORT_PREFIX, id);
				String hoverText = String.format("%s - %d, %d, %d | type: %s", world, x, y, z, typeId);

				var message = new ComponentBuilder(GanglandChatUtil.color(" &b- &7" + id + " &8[&7" + typeId + "&8] "))
						.append(GanglandChatUtil.color("&e(&btp&e)"))
						.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
						.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverText)))
						.create();

				sender.spigot().sendMessage(message);
			});
		};
	}
}
