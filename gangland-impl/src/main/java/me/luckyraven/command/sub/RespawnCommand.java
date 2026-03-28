package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.listener.player.CustomPlayerDeath;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.downed.DownedPlayerRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RespawnCommand extends CommandHandler {

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

		CustomPlayerDeath.triggerManualRespawn(player);
	}

	@Override
	protected void initializeArguments() { }

	@Override
	protected void help(CommandSender sender, int page) { }

}
