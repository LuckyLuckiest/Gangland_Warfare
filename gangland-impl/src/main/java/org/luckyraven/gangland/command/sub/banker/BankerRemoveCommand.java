package org.luckyraven.gangland.command.sub.banker;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerManager;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerNpc;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;

class BankerRemoveCommand extends SubArgument {

	private static final double MAX_TARGET_DISTANCE = 5D;

	private final BankerManager bankerManager;

	protected BankerRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              BankerManager bankerManager) {
		super(gangland, "remove", tree, parent);
		this.bankerManager = bankerManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			Entity targeted = rayTraceEntity(player);
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

	private Entity rayTraceEntity(Player player) {
		Location eye       = player.getEyeLocation();
		Vector   direction = eye.getDirection();

		RayTraceResult result = player.getWorld().rayTraceEntities(eye, direction, MAX_TARGET_DISTANCE,
		                                                           entity -> !entity.equals(player));
		return result != null ? result.getHitEntity() : null;
	}

}
