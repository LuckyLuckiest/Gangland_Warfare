package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.DoubleArgument;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.UpdateChecker;
import me.luckyraven.util.command.CommandHandler;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

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
			String message = GanglandChatUtil.commandMessage(
					"There is a new update, please type:\n&7/" + Gangland.SHORT_PREFIX + " update download");

			commandSender.sendMessage(message);
		} else {
			commandSender.sendMessage(GanglandChatUtil.commandMessage("You are running the latest version."));
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
				sender.sendMessage(GanglandChatUtil.commandMessage("You are already running the latest version."));
				return;
			}

			sender.sendMessage(GanglandChatUtil.commandMessage("Downloading the latest version..."));
			boolean downloadSuccess = getGangland().getUpdateChecker().downloadLatestVersion();

			if (downloadSuccess) {
				sender.sendMessage(GanglandChatUtil.commandMessage("Download successful!"));
			} else {
				sender.sendMessage(GanglandChatUtil.commandMessage("Download failed!"));
			}
		}, getPermission() + ".download");
	}
}
