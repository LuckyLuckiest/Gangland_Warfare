package me.luckyraven.command;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.HelpInfo;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public abstract class CommandHandler {

	private @Getter
	final String      label;
	private @Getter
	final Set<String> alias;
	private @Getter
	final String      permission;
	private @Getter
	final boolean     user;

	private @Getter
	final         HelpInfo   helpInfo;
	private final JavaPlugin plugin;

	public CommandHandler(JavaPlugin plugin, String label, boolean user, String... alias) {
		this.plugin = plugin;
		this.label = label.toLowerCase();
		this.alias = new HashSet<>();
		for (String s : alias) this.alias.add(s.toLowerCase());
		this.permission = "gangland.command." + label;
		this.user = user;
		this.helpInfo = new HelpInfo();
	}

	public abstract void onExecute(CommandSender sender, Command command, String[] args);

	public abstract void help(CommandSender sender, int page);

	public void runExecute(CommandSender sender, Command command, String[] args) {
		if (!sender.hasPermission(permission)) {
			sender.sendMessage(ChatUtil.color("&cError&8: &7Not permissible!"));
			return;
		}
		if (user && !(sender instanceof Player)) {
			plugin.getLogger().info("Need to be executed as a player.");
			return;
		}
		onExecute(sender, command, args);
	}

	public CommandInformation getCommandInformation(String info) {
		if (plugin instanceof Gangland)
			return ((Gangland) plugin).getInitializer().getInformationManager().getCommands().get(info);
		return null;
	}

}
