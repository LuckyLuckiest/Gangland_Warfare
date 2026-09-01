package org.luckyraven.gangland.command.sub.civilians.spawner;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.civilian.spawn.CivilianSpawner;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

class CivilianSpawnerInfoCommand extends SubArgument {

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final CivilianSpawnManager civilianSpawnManager;

	CivilianSpawnerInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                           CivilianSpawnManager civilianSpawnManager) {
		super(gangland, "info", tree, parent);

		this.gangland             = gangland;
		this.tree                 = tree;
		this.civilianSpawnManager = civilianSpawnManager;

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

			CivilianSpawner spawner = civilianSpawnManager.getSpawners()
					.stream()
					.filter(s -> s.getId() == id).findFirst().orElse(null);

			if (spawner == null) {
				sender.sendMessage(Messages.LOCATION_NOT_FOUND.toString().replace("%location%", idStr));
				return;
			}

			Location location = spawner.getLocation();
			int      x        = location.getBlockX();
			int      y        = location.getBlockY();
			int      z        = location.getBlockZ();
			String   world    = location.getWorld() != null ? location.getWorld().getName() : "?";
			String   typeId   = spawner.getTypeId() != null ? spawner.getTypeId() : "any";
			String   groupId  = spawner.getGroupId() != null ? spawner.getGroupId() : "none";

			String tpCommand = String.format("/%s civilian spawner teleport %d", Gangland.SHORT_PREFIX, id);

			String color = GanglandChatUtil.color("&7&lCivilian spawner &e(&b" + id + "&e)&7: ");
			var message = new ComponentBuilder(color).append(GanglandChatUtil.color("&e(&btp&e)"))
			                                         .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
			                                         .create();

			sender.spigot().sendMessage(message);

			String info = String.format(
					" &bX: &7%d\n &bY: &7%d\n &bZ: &7%d\n &bWorld: &7%s\n &bType: &7%s\n &bGroup: &7%s",
					x, y, z, world, typeId, groupId);

			sender.sendMessage(GanglandChatUtil.color(info));
		}, sender -> civilianSpawnManager.getSpawnerIds()
				.stream().map(String::valueOf).toList());

		this.addSubArgument(idArg);
	}
}
