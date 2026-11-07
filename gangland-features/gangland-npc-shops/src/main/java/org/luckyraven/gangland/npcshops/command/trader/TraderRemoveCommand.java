package org.luckyraven.gangland.npcshops.command.trader;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.npcshops.NpcTargeting;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.npcshops.trader.TraderManager;
import org.luckyraven.gangland.npcshops.trader.TraderNpc;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;

class TraderRemoveCommand extends SubArgument {

	private static final double        MAX_TARGET_DISTANCE = 5D;
	private final        TraderManager traderManager;

	protected TraderRemoveCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                              TraderManager traderManager) {
		super(plugin, "remove", tree, parent);
		this.traderManager = traderManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			Entity targeted = NpcTargeting.rayTraceEntity(player, MAX_TARGET_DISTANCE);
			if (targeted == null) {
				player.sendMessage(Messages.TRADER_LOOK_AT.toString()
				                                          .replace("%range%",
				                                                   String.valueOf((int) MAX_TARGET_DISTANCE)));
				return;
			}

			TraderNpc trader = traderManager.getByEntity(targeted);
			if (trader == null) {
				player.sendMessage(Messages.TRADER_NOT_A_TRADER.toString());
				return;
			}

			traderManager.remove(trader.getData().getId());
			player.sendMessage(Messages.TRADER_REMOVED.toString());
		};
	}

}
