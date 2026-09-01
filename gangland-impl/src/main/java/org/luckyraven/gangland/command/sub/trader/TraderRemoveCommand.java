package org.luckyraven.gangland.command.sub.trader;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderManager;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderNpc;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;

class TraderRemoveCommand extends SubArgument {

	private static final double        MAX_TARGET_DISTANCE = 5D;
	private final        TraderManager traderManager;

	protected TraderRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              TraderManager traderManager) {
		super(gangland, "remove", tree, parent);
		this.traderManager = traderManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			Entity targeted = rayTraceEntity(player);
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

	private Entity rayTraceEntity(Player player) {
		Location eye       = player.getEyeLocation();
		Vector   direction = eye.getDirection();

		RayTraceResult result = player.getWorld().rayTraceEntities(eye, direction, MAX_TARGET_DISTANCE,
		                                                           entity -> !entity.equals(player));
		return result != null ? result.getHitEntity() : null;
	}

}
