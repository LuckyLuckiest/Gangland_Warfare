package me.luckyraven.command.sub.copsncrooks;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class UnjailCommand extends CommandHandler {

	public UnjailCommand(Gangland gangland) {
		super(gangland, "unjail", false);

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("unjail"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		commandSender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<player>"));
	}

	@Override
	protected void initializeArguments() {
		String notFound = MessageAddon.PLAYER_NOT_FOUND.toString();

		Argument playerArg = new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			String playerStr = args[1];
			Player target    = Bukkit.getPlayer(playerStr);

			if (target == null) {
				sender.sendMessage(notFound.replace("%player%", playerStr));
				return;
			}

			DetainmentService detainmentService = getGangland().getInitializer().getDetainmentService();
			detainmentService.release(target);

			sender.sendMessage(ChatUtil.commandMessage("&aReleased &e" + target.getName() + "&a from jail."));
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());

		getArgument().addSubArgument(playerArg);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Unjail");
	}
}
