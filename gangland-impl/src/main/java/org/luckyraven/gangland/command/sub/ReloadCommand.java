package org.luckyraven.gangland.command.sub;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.gangland.bootstrap.PeriodicalUpdates;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CustomLog
@CommandHandler
public final class ReloadCommand extends Command {

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
		reloadProcess("", () -> getGangland().getReloadPlugin().reload(), true);
	}

	@Override
	protected void initializeArguments() {
		Argument files = new Argument(getGangland(), new String[]{"files", "file"}, getArgumentTree(),
		                              (argument, sender, args) -> {
										  reloadProcess("files", () -> getGangland().getReloadPlugin().filesReload(),
			                                            true);
									  });

		Argument scoreboard = new Argument(getGangland(), "scoreboard", getArgumentTree(), (argument, sender, args) -> {
			reloadProcess("scoreboard", () -> {
				if (Settings.isScoreboardEnabled()) {
					getGangland().getReloadPlugin().scoreboardReload();
				}
			}, false);
		});

		GanglandContext context = getGangland().getContext();

		Argument inventory = new Argument(getGangland(), "inventory", getArgumentTree(), (argument, sender, args) -> {
			reloadProcess("inventory", () -> {
				context.get(PeriodicalUpdates.class).resetCache();
				getGangland().getReloadPlugin().inventoryReload();
			}, false);
		});

		Argument cleanup = new Argument(getGangland(), "cleanup", getArgumentTree(), (argument, sender, args) -> {
			reloadProcess("cleanup", () -> {
				context.get(PeriodicalUpdates.class).getCleanupService().forceCleanup();
			}, false);
		});

		List<Argument> arguments = new ArrayList<>();

		arguments.add(files);
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

		GanglandChatUtil.sendToOperators(permission, reloading);

		PeriodicalUpdates updates = forceUpdate
		                            ? getGangland().getContext().get(PeriodicalUpdates.class)
		                            : null;

		if (updates == null) {
			runReloadBody(permission, runnable);
			return;
		}

		// Wait for every async repository upsert to finish before wiping caches and reloading. Otherwise
		// loadAll() in the reload pass can race the pending writes, repopulate caches from stale DB state,
		// and the next auto-save tick then overwrites the fresh DB rows with that stale cache.
		updates.forceUpdate(
				() -> Bukkit.getScheduler().runTask(getGangland(), () -> runReloadBody(permission, runnable)));
	}

	private void runReloadBody(String permission, Runnable runnable) {
		try {
			runnable.run();

			String reloadComplete = "&aReload has been completed.";

			GanglandChatUtil.sendToOperators(permission, reloadComplete);
		} catch (Throwable throwable) {
			String reloadIssue = "&cThere was a problem reloading the plugin!";

			GanglandChatUtil.sendToOperators(permission, reloadIssue);
			log.error(throwable.getMessage(), throwable);
		}
	}

}
