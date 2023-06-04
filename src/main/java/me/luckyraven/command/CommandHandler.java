package me.luckyraven.command;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.data.HelpInfo;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public abstract class CommandHandler {

	private @Getter
	final String label;

	private @Getter
	final Set<String> alias;

	private @Getter
	final String permission;

	private @Getter
	final boolean user;

	private @Getter
	final HelpInfo helpInfo;

	public CommandHandler(String label, boolean user, String... alias) {
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
			Gangland.getInstance().getLogger().info("Need to be executed from a player.");
			return;
		}
		onExecute(sender, command, args);
	}

}
