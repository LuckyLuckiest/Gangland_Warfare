package org.luckyraven.gangland.copsncrooks.command.cuff;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.file.configuration.Messages;

import java.util.Map;

@CommandHandler
public final class CuffCommand extends Command {

	private final DetainmentService detainmentService;
	private final CommandMessages   commandMessages;

	public CuffCommand(JavaPlugin plugin, DetainmentService detainmentService, CommandMessages commandMessages) {
		super(plugin, "cuff", false);

		this.detainmentService = detainmentService;
		this.commandMessages   = commandMessages;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("cuff"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		commandSender.sendMessage(commandMessages.usage("/glw cuff <player>"));
	}

	@Override
	protected void initializeArguments() {
		Argument playerArg = getPlayerArg();

		getArgument().addSubArgument(playerArg);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Cuff");
	}

	private Argument getPlayerArg() {
		return new OptionalArgument(getPlugin(), getArgumentTree(), (argument, sender, args) -> {
			String playerStr = args[1];
			Player target    = Bukkit.getPlayer(playerStr);

			if (target == null) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.PLAYER_NOT_FOUND, Map.of("player", playerStr)));
				return;
			}

			if (detainmentService.isHandcuffed(target)) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.CUFF_ALREADY_CUFFED, Map.of()));
				return;
			}

			detainmentService.handcuff(target);

			sender.sendMessage(commandMessages.format(CommandMessages.Key.CUFF_HANDCUFFED, Map.of("target", target.getName())));
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().filter(player -> !detainmentService.isHandcuffed(player)).map(Player::getName).toList());
	}
}
