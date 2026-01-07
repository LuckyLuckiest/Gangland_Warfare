package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ReloadCommand extends CommandHandler {

	private static final Logger logger = LogManager.getLogger(ReloadCommand.class.getSimpleName());

	public ReloadCommand(Gangland gangland) {
		super(gangland, "reload", false, "rl");

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("reload"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		reloadProcess("", () -> getGangland().getReloadPlugin().reload(true), true);
	}

	@Override
	protected void initializeArguments() {
		Argument files = new Argument(getGangland(), new String[]{"files", "file"}, getArgumentTree(),
									  (argument, sender, args) -> {
										  reloadProcess("files", () -> getGangland().getReloadPlugin().filesReload(),
														true);
									  });

		Argument data = new Argument(getGangland(), new String[]{"database", "data"}, getArgumentTree(),
									 (argument, sender, args) -> {
										 reloadProcess("database",
													   () -> getGangland().getReloadPlugin().databaseInitialize(true),
													   true);
									 });

		Argument scoreboard = new Argument(getGangland(), "scoreboard", getArgumentTree(), (argument, sender, args) -> {
			reloadProcess("scoreboard", () -> {
				if (SettingAddon.isScoreboardEnabled()) getGangland().getReloadPlugin().scoreboardReload();
			}, false);
		});

		Argument inventory = new Argument(getGangland(), "inventory", getArgumentTree(), (argument, sender, args) -> {
			reloadProcess("inventory", () -> {
				getGangland().getPeriodicalUpdates().resetCache();
				getGangland().getReloadPlugin().inventoryReload();
			}, false);
		});

		Argument cleanup = new Argument(getGangland(), "cleanup", getArgumentTree(), (argument, sender, args) -> {
			reloadProcess("cleanup", () -> {
				getGangland().getPeriodicalUpdates().getCleanupService().forceCleanup();
			}, false);
		});

		List<Argument> arguments = new ArrayList<>();

		arguments.add(files);
		arguments.add(data);
		arguments.add(scoreboard);
		arguments.add(inventory);
		arguments.add(cleanup);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Reload");
	}

	private void reloadProcess(String process, Runnable runnable, boolean forceUpdate) {
		String permission = getPermission();
		String reloading  = String.format("&bReloading&7 the plugin%s%s...", process.isEmpty() ? "" : " ", process);

		ChatUtil.sendToOperators(permission, reloading);

		try {
			if (forceUpdate) getGangland().getPeriodicalUpdates().forceUpdate();
			runnable.run();

			String reloadComplete = "&aReload has been completed.";

			ChatUtil.sendToOperators(permission, reloadComplete);
		} catch (Throwable throwable) {
			String reloadIssue = "&cThere was a problem reloading the plugin!";

			ChatUtil.sendToOperators(permission, reloadIssue);
			logger.error(throwable.getMessage(), throwable);
		}
	}

}
