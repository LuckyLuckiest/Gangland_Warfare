package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;
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
		reloadProcess("", () -> {
			getGangland().getReloadPlugin().filesReload();
			databaseReload();
			scoreboardReload();
			getGangland().getReloadPlugin().periodicalUpdatesReload();
		}, true);
	}

	@Override
	protected void initializeArguments() {
		Argument files = new Argument(new String[]{"files", "file"}, getArgumentTree(), (argument, sender, args) -> {
			reloadProcess("files", () -> getGangland().getReloadPlugin().filesReload(), true);
		});

		Argument data = new Argument(new String[]{"database", "data"}, getArgumentTree(), (argument, sender, args) -> {
			reloadProcess("database", this::databaseReload, true);
		});

		Argument scoreboard = new Argument("scoreboard", getArgumentTree(), (argument, sender, args) -> {
			reloadProcess("scoreboard", this::scoreboardReload, false);
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

	private void reloadProcess(String process, Runnable runnable, boolean forceUpdate) {
		String permission = "gangland.command.reload";
		String reloading  = String.format("&bReloading&7 the plugin%s%s...", process.isEmpty() ? "" : " ", process);

		sendToOperators(permission, reloading);
		try {
			if (forceUpdate) getGangland().getPeriodicalUpdates().forceUpdate();
			runnable.run();

			String reloadComplete = "&aReload has been completed.";

			sendToOperators(permission, reloadComplete);
		} catch (Throwable throwable) {
			String reloadIssue = "&cThere was a problem reloading the plugin!";

			sendToOperators(permission, reloadIssue);
			Gangland.getLog4jLogger().error(throwable.getMessage(), throwable);
		}
	}

	private void scoreboardReload() {
		if (SettingAddon.isScoreboardEnabled()) getGangland().getReloadPlugin().scoreboardReload();
	}

	private void databaseReload() {
		getGangland().getReloadPlugin().databaseInitialize(true);
	}

	private void sendToOperators(String permission, String message) {
		Bukkit.getServer().getOnlinePlayers().stream().filter(player -> player.hasPermission(permission)).forEach(
				player -> player.sendMessage(ChatUtil.commandMessage(message)));

		Bukkit.getServer().getConsoleSender().sendMessage(ChatUtil.commandMessage(message));
	}

}
