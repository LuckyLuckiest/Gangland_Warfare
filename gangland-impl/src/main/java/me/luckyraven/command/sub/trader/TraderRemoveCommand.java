package me.luckyraven.command.sub.trader;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

class TraderRemoveCommand extends SubArgument {

	private final TraderManager traderManager;

	protected TraderRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              TraderManager traderManager) {
		super(gangland, "remove", tree, parent);
		this.traderManager = traderManager;
	}

	private static final double MAX_TARGET_DISTANCE = 5D;

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			Entity targeted = rayTraceEntity(player);
			if (targeted == null) {
				player.sendMessage(GanglandChatUtil.commandMessage(
						"&cLook at a trader within 5 blocks."));
				return;
			}

			TraderNpc trader = traderManager.getByEntity(targeted);
			if (trader == null) {
				player.sendMessage(GanglandChatUtil.commandMessage(
						"&cThat entity is not a trader."));
				return;
			}

			traderManager.remove(trader.getData().getId());
			player.sendMessage(GanglandChatUtil.commandMessage("&aTrader removed."));
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
