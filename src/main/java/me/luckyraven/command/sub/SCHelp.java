package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.data.HelpInfo;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class SCHelp extends CommandHandler {

	public SCHelp(Gangland gangland) {
		super(gangland, "help", false, "general", "?");
		getHelpInfo().add(getCommandInformation("general"));
		getHelpInfo().add(getCommandInformation("general_page"));
		HelpInfo info;
		for (Map.Entry<String, CommandHandler> entry : gangland.getInitializer()
		                                                       .getCommandManager()
		                                                       .getCommands()
		                                                       .entrySet()) {
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
