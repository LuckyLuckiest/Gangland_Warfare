package me.luckyraven.command.argument;

import org.bukkit.command.CommandSender;

@FunctionalInterface
public interface ArgumentAction {

	void execute(CommandSender sender, String[] args);

}
