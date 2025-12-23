package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class DownloadResourceCommand extends CommandHandler {

	public DownloadResourceCommand(Gangland gangland) {
		super(gangland, "resource", true, "download");

		var list = getCommands().entrySet()
								.stream()
								.filter(entry -> entry.getKey().startsWith("resource"))
								.sorted(Map.Entry.comparingByKey())
								.map(Map.Entry::getValue)
								.toList();

		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		if (!SettingAddon.isScoreboardEnabled()) return;

		Player player = (Player) commandSender;

		player.setResourcePack(SettingAddon.getResourcePackUrl());
	}

	@Override
	protected void initializeArguments() { }

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Resource Pack");
	}
}
