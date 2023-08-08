package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.file.FileManager;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class ReloadCommand extends CommandHandler {

	private final Gangland gangland;

	public ReloadCommand(Gangland gangland) {
		super(gangland, "reload", false, "rl");

		this.gangland = gangland;
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		reloadProcess(commandSender, "", () -> {
			FileManager fileManager = gangland.getInitializer().getFileManager();

			fileManager.reloadFiles();
			gangland.getReloadPlugin().addonsLoader();
			databaseReload();
		});
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		Argument files = new Argument(new String[]{"files", "file"}, getArgumentTree(), (argument, sender, args) -> {
			reloadProcess(sender, "files", () -> {
				FileManager fileManager = gangland.getInitializer().getFileManager();

				fileManager.reloadFiles();
				gangland.getReloadPlugin().addonsLoader();
			});
		});

		Argument data = new Argument(new String[]{"database", "data"}, getArgumentTree(), (argument, sender, args) -> {
			reloadProcess(sender, "database", this::databaseReload);
		});

		List<Argument> arguments = new ArrayList<>();

		arguments.add(files);
		arguments.add(data);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {

	}

	private void reloadProcess(CommandSender sender, String process, Runnable runnable) {
		sender.sendMessage(ChatUtil.commandMessage(
				String.format("&bReloading&7 the plugin%s%s...", process.isEmpty() ? "" : " ", process)));
		try {
			runnable.run();

			sender.sendMessage(ChatUtil.commandMessage("&aReload has been completed."));
		} catch (Exception exception) {
			sender.sendMessage(ChatUtil.commandMessage("&cThere was a problem reloading the plugin!"));
		}
	}

	private void databaseReload() {
		gangland.getReloadPlugin().userInitialize(true);
		gangland.getReloadPlugin().gangInitialize(true);
		gangland.getReloadPlugin().rankInitialize(true);
	}

}
