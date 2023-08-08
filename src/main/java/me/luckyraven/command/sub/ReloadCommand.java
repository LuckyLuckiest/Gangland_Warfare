package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.file.FileManager;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;

public class ReloadCommand extends CommandHandler {

	private final Gangland gangland;

	public ReloadCommand(Gangland gangland) {
		super(gangland, "reload", false, "rl");

		this.gangland = gangland;
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		FileManager fileManager = gangland.getInitializer().getFileManager();

		commandSender.sendMessage(ChatUtil.commandMessage("&bReloading&7 the plugin..."));
		try {
			fileManager.reloadFiles();
			gangland.getReloadPlugin().addonsLoader();
			gangland.getReloadPlugin().userInitialize();

			commandSender.sendMessage(ChatUtil.commandMessage("&aReload has been completed."));
		} catch (Exception exception) {
			commandSender.sendMessage(ChatUtil.commandMessage("&cThere was a problem reloading the plugin!"));
		}
	}

	@Override
	protected void initializeArguments(Gangland gangland) {

	}

	@Override
	protected void help(CommandSender sender, int page) {

	}

}
