package org.luckyraven.gangland.command;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.luckyraven.gangland.data.gang.GangMembership;

import java.util.List;

public final class GangShortLabelExecutor extends org.bukkit.command.Command {

	public GangShortLabelExecutor(String label, List<String> prefix, String permission, CommandExecutor executor,
	                              TabCompleter completer, org.bukkit.command.Command target, String targetLabel) {
		super(label);
	}

	@Override
	public boolean execute(CommandSender sender, String commandLabel, String[] args) {
		return false;
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
		return List.of();
	}

	public static int registerIfInstalled(String fallbackPrefix, CommandMap map, GangMembership membership,
	                                      CommandExecutor executor, TabCompleter completer,
	                                      org.bukkit.command.Command target, String targetLabel) {
		return 0;
	}

}
