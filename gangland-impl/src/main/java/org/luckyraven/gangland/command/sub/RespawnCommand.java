package org.luckyraven.gangland.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.core.bean.command.CommandHandler;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.listener.player.CustomPlayerDeathListener;
import org.luckyraven.gangland.util.GanglandChatUtil;

@CommandHandler
public final class RespawnCommand extends Command {

	public RespawnCommand(Gangland gangland) {
		super(gangland, "respawn", true);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player player = (Player) commandSender;

		if (!DownedPlayerRegistry.isDowned(player.getUniqueId())) {
			player.sendMessage(GanglandChatUtil.color("&cYou do not have a pending respawn."));
			return;
		}

		CustomPlayerDeathListener.triggerManualRespawn(player);
	}

	@Override
	protected void initializeArguments() { }

	@Override
	protected void help(CommandSender sender, int page) { }

}
