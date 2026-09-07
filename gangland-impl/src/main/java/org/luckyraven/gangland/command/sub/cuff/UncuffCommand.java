package org.luckyraven.gangland.command.sub.cuff;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Map;

@CommandHandler
public final class UncuffCommand extends Command {

	private final DetainmentService detainmentService;

	public UncuffCommand(Gangland gangland, DetainmentService detainmentService) {
		super(gangland, "uncuff", false);

		this.detainmentService = detainmentService;

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
		commandSender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<player>"));
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
		return new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			String playerStr = args[1];
			Player target    = Bukkit.getPlayer(playerStr);

			if (target == null) {
				sender.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", playerStr));
				return;
			}

			if (!detainmentService.isHandcuffed(target)) {
				sender.sendMessage(Messages.CUFF_NOT_CUFFED.toString());
				return;
			}

			releasePlayer(sender, target);
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().filter(detainmentService::isHandcuffed).map(Player::getName).toList());
	}

	private void releasePlayer(CommandSender sender, Player target) {
		detainmentService.release(target);

		sender.sendMessage(Messages.CUFF_RELEASED.toString().replace("%target%", target.getName()));
	}
}
