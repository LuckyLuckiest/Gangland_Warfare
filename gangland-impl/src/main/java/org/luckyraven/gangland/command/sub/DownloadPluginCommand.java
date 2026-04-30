package org.luckyraven.gangland.command.sub;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.types.DoubleArgument;
import org.luckyraven.gangland.core.bean.command.CommandHandler;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.UpdateChecker;

import java.util.Map;

@CommandHandler
public final class DownloadPluginCommand extends Command {

	public DownloadPluginCommand(Gangland gangland) {
		super(gangland, "update", false);
		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("update"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();

		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		// check if there was an update
		boolean newUpdate = !getGangland().getUpdateChecker()
		                                  .getLatestVersion()
		                                  .equalsIgnoreCase(getGangland().getDescription().getVersion());

		if (newUpdate) {
			commandSender.sendMessage(Messages.UPDATE_AVAILABLE.toString()
			                                                   .replace("%short_prefix%", Gangland.SHORT_PREFIX));
		} else {
			commandSender.sendMessage(Messages.UPDATE_LATEST.toString());
		}
	}

	@Override
	protected void initializeArguments() {
		Argument confirm = getConfirm();

		getArgument().addSubArgument(confirm);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Update Checker");
	}

	private @NotNull Argument getConfirm() {
		return new DoubleArgument(getGangland(), "download", getArgumentTree(), (argument, sender, args) -> {
			UpdateChecker updateChecker = getGangland().getUpdateChecker();
			boolean       newUpdate     = updateChecker.updateAvailable();

			if (!newUpdate) {
				sender.sendMessage(Messages.UPDATE_ALREADY_LATEST.toString());
				return;
			}

			sender.sendMessage(Messages.UPDATE_DOWNLOADING.toString());
			boolean downloadSuccess = getGangland().getUpdateChecker().downloadLatestVersion();

			if (downloadSuccess) {
				sender.sendMessage(Messages.UPDATE_DOWNLOAD_SUCCESS.toString());
			} else {
				sender.sendMessage(Messages.UPDATE_DOWNLOAD_FAILED.toString());
			}
		}, getPermission() + ".download");
	}
}
