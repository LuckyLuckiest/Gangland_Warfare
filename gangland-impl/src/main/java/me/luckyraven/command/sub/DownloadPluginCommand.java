package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.DoubleArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DownloadPluginCommand extends CommandHandler {

	private final Map<CommandSender, Boolean> containsUpdate;

	public DownloadPluginCommand(Gangland gangland) {
		super(gangland, "update", false);

		this.containsUpdate = new HashMap<>();
		List<CommandInformation> list = getCommands().entrySet()
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

		containsUpdate.put(commandSender, newUpdate);

		if (newUpdate) {
			commandSender.sendMessage(
					ChatUtil.commandMessage("There is a new update, please type:\n&7/glw update download"));
		} else {
			commandSender.sendMessage(ChatUtil.commandMessage("You are running the latest version."));
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
			if (!containsUpdate.containsKey(sender)) return;

			boolean newUpdate = containsUpdate.get(sender);

			if (!newUpdate) return;

			getGangland().getUpdateChecker().downloadLatestVersion();
		}, getPermission() + ".download");
	}
}
