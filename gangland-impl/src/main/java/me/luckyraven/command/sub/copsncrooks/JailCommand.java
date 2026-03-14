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

public final class JailCommand extends CommandHandler {

	public JailCommand(Gangland gangland) {
		super(gangland, "jail", false);

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("jail"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		commandSender.sendMessage(
				ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<player|create|remove>"));
	}

	@Override
	protected void initializeArguments() {
		Argument createArg = new JailCreateCommand(getGangland(), getArgumentTree(), getArgument());
		Argument removeArg = new JailRemoveCommand(getGangland(), getArgumentTree(), getArgument());
		Argument playerArg = jailPlayerArgument();

		getArgument().addSubArgument(createArg);
		getArgument().addSubArgument(removeArg);
		getArgument().addSubArgument(playerArg);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Jail");
	}

	private Argument jailPlayerArgument() {
		String notFound = MessageAddon.PLAYER_NOT_FOUND.toString();

		Argument playerArg = new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			String playerStr = args[1];
			Player target    = Bukkit.getPlayer(playerStr);

			if (target == null) {
				sender.sendMessage(notFound.replace("%player%", playerStr));
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<jailId>"));
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());

		Argument jailIdArg = new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			String playerStr = args[1];
			Player target    = Bukkit.getPlayer(playerStr);

			if (target == null) {
				sender.sendMessage(notFound.replace("%player%", playerStr));
				return;
			}

			String jailIdStr = args[2];
			int    jailId;
			try {
				jailId = Integer.parseInt(jailIdStr);
			} catch (NumberFormatException e) {
				sender.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", jailIdStr));
				return;
			}

			DetainmentService detainmentService = getGangland().getInitializer().getDetainmentService();
			detainmentService.jail(target, jailId);

			sender.sendMessage(
					ChatUtil.commandMessage("&aJailed &e" + target.getName() + "&a in jail &e" + jailId + "&a."));
		}, sender -> getGangland().getInitializer().getJailManager().getJailService().getCells()
				.stream().map(jail -> String.valueOf(jail.getId())).toList());

		playerArg.addSubArgument(jailIdArg);
		return playerArg;
	}
}
