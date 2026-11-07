package org.luckyraven.gangland.npcshops.command.banker;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.npcshops.NpcTargeting;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.npcshops.banker.BankerManager;
import org.luckyraven.gangland.npcshops.banker.BankerNpc;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;

class BankerRemoveCommand extends SubArgument {

	private static final double MAX_TARGET_DISTANCE = 5D;

	private final BankerManager bankerManager;

	protected BankerRemoveCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                              BankerManager bankerManager) {
		super(plugin, "remove", tree, parent);
		this.bankerManager = bankerManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			Entity targeted = NpcTargeting.rayTraceEntity(player, MAX_TARGET_DISTANCE);
			if (targeted == null) {
				player.sendMessage(Messages.BANKER_LOOK_AT.toString()
				                                          .replace("%range%",
				                                                   String.valueOf((int) MAX_TARGET_DISTANCE)));
				return;
			}

			BankerNpc banker = bankerManager.getByEntity(targeted);
			if (banker == null) {
				player.sendMessage(Messages.BANKER_NOT_A_BANKER.toString());
				return;
			}

			bankerManager.remove(banker.getData().getId());
			player.sendMessage(Messages.BANKER_REMOVED.toString());
		};
	}

}
