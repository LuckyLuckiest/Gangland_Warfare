package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.core.bean.command.CommandHandler;
import me.luckyraven.core.downed.DownedPlayerRegistry;
import me.luckyraven.listener.player.CustomPlayerDeathListener;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
