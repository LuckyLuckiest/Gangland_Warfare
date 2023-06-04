package me.luckyraven.command.sub;

import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.HelpInfo;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class SCHelp extends CommandHandler {

	public SCHelp() {
		super("help", false, "general", "?");
		getHelpInfo().add(CommandInformation.getInfo("general"));
		getHelpInfo().add(CommandInformation.getInfo("general_page"));
		HelpInfo info;
		for (Map.Entry<String, CommandHandler> entry : CommandManager.getCommands().entrySet()) {
			info = entry.getValue().getHelpInfo();
			for (int i = 0; i < info.size(); i++) getHelpInfo().add(info.getInformation(i));
		}
	}

	@Override
	public void onExecute(CommandSender sender, Command command, String[] args) {
		for (String arg : args)
			if (getAlias().contains(arg)) {
				help(sender, 1);
				break;
			}
	}

	@Override
	public void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Help");
	}

}
