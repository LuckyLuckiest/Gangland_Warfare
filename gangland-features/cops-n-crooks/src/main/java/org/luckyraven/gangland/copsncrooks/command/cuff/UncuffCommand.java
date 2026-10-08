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

import java.util.Map;

@CommandHandler
public final class UncuffCommand extends Command {

	private final DetainmentService detainmentService;
	private final CommandMessages   commandMessages;

	public UncuffCommand(JavaPlugin plugin, DetainmentService detainmentService, CommandMessages commandMessages) {
		super(plugin, "uncuff", false);

		this.detainmentService = detainmentService;
		this.commandMessages   = commandMessages;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("uncuff"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		if (commandSender instanceof Player player && detainmentService.isHandcuffed(player)) {
			releasePlayer(commandSender, player);
			return;
		}
		commandSender.sendMessage(commandMessages.usage("/glw uncuff <player>"));
	}

	@Override
	protected void initializeArguments() {
		Argument playerArg = getPlayerArg();

		getArgument().addSubArgument(playerArg);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Uncuff");
	}

	private Argument getPlayerArg() {
		return new OptionalArgument(getPlugin(), getArgumentTree(), (argument, sender, args) -> {
			String playerStr = args[1];
			Player target    = Bukkit.getPlayer(playerStr);

			if (target == null) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.PLAYER_NOT_FOUND,
                                                          Map.of("player", playerStr)));
				return;
			}

			if (!detainmentService.isHandcuffed(target)) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.CUFF_NOT_CUFFED, Map.of()));
				return;
			}

			releasePlayer(sender, target);
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().filter(detainmentService::isHandcuffed).map(Player::getName).toList());
	}

	private void releasePlayer(CommandSender sender, Player target) {
		detainmentService.release(target);

		sender.sendMessage(commandMessages.format(CommandMessages.Key.CUFF_RELEASED,
                                                  Map.of("target", target.getName())));
	}
}
