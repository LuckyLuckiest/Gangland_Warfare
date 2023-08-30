package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReloadCommand extends CommandHandler {


	public ReloadCommand(Gangland gangland) {
		super(gangland, "reload", false, "rl");

		List<CommandInformation> list = getCommands().entrySet().stream().filter(
				entry -> entry.getKey().startsWith("reload")).sorted(Map.Entry.comparingByKey()).map(
				Map.Entry::getValue).toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		reloadProcess(commandSender, "", () -> {
			getGangland().getReloadPlugin().filesReload();
			databaseReload();
			getGangland().getReloadPlugin().scoreboardReload();
			getGangland().getReloadPlugin().periodicalUpdatesReload();
		}, true);
	}

	@Override
	protected void initializeArguments() {
		Argument files = new Argument(new String[]{"files", "file"}, getArgumentTree(), (argument, sender, args) -> {
			reloadProcess(sender, "files", () -> getGangland().getReloadPlugin().filesReload(), true);
		});

		Argument data = new Argument(new String[]{"database", "data"}, getArgumentTree(), (argument, sender, args) -> {
			reloadProcess(sender, "database", this::databaseReload, true);
		});

		Argument scoreboard = new Argument("scoreboard", getArgumentTree(), (argument, sender, args) -> {
			reloadProcess(sender, "scoreboard", () -> getGangland().getReloadPlugin().scoreboardReload(), false);
		});

		List<Argument> arguments = new ArrayList<>();

		arguments.add(files);
		arguments.add(data);
		arguments.add(scoreboard);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Reload");
	}

	private void reloadProcess(CommandSender sender, String process, Runnable runnable, boolean forceUpdate) {
		sender.sendMessage(ChatUtil.commandMessage(
				String.format("&bReloading&7 the plugin%s%s...", process.isEmpty() ? "" : " ", process)));
		try {
			if (forceUpdate) getGangland().getPeriodicalUpdates().forceUpdate();
			runnable.run();

			sender.sendMessage(ChatUtil.commandMessage("&aReload has been completed."));
		} catch (Exception exception) {
			sender.sendMessage(ChatUtil.commandMessage("&cThere was a problem reloading the plugin!"));
		}
	}

	private void databaseReload() {
		getGangland().getReloadPlugin().databaseInitialize(true);
	}

}
