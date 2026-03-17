package me.luckyraven.command.sub.copsncrooks;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class CopSpawnerCommand extends CommandHandler {

	public CopSpawnerCommand(Gangland gangland) {
		super(gangland, "copspawner", false);

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("copspawner"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		commandSender.sendMessage(ChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<set|remove>"));
	}

	@Override
	protected void initializeArguments() {
		Argument setArg    = new CopSpawnerSetCommand(getGangland(), getArgumentTree(), getArgument());
		Argument removeArg = new CopSpawnerRemoveCommand(getGangland(), getArgumentTree(), getArgument());

		getArgument().addSubArgument(setArg);
		getArgument().addSubArgument(removeArg);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Cop Spawner");
	}
}
