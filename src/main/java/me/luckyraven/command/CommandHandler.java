package me.luckyraven.command;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.HelpInfo;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class CommandHandler {

	@Getter
	private final String         label;
	@Getter
	private final Set<String>    alias;
	@Getter
	private final String         permission;
	@Getter
	private final boolean        user;
	@Getter
	private final HelpInfo       helpInfo;
	@Getter
	private final Argument       argument;
	@Getter
	private final Tree<Argument> argumentTree;

	private final  Gangland          gangland;

	public CommandHandler(Gangland gangland, String label, boolean user, String... alias) {
		this.gangland = gangland;
		this.label = label.toLowerCase();

		this.alias = new HashSet<>();
		for (String s : alias) this.alias.add(s.toLowerCase());

		this.permission = "gangland.command." + label;
		this.user = user;
		this.helpInfo = new HelpInfo();
		this.argumentTree = new Tree<>();

		String[] args = new String[alias.length + 1];
		args[0] = label;
		System.arraycopy(alias, 0, args, 1, args.length - 1);

		this.argument = new Argument(args, argumentTree, this::onExecute);
		this.argumentTree.add(argument.getNode());

		initializeArguments(gangland);
	}

	protected abstract void onExecute(Argument argument, CommandSender commandSender, String[] arguments);

	protected abstract void initializeArguments(Gangland gangland);

	protected abstract void help(CommandSender sender, int page);

	public void runExecute(CommandSender sender, String[] args) {
		// sender has the permission
		if (!sender.hasPermission(permission)) {
			sender.sendMessage(ChatUtil.color("&cError&8: &7Not permissible!"));
			return;
		}

		// check if the user should be a Player
		if (user && !(sender instanceof Player)) {
			gangland.getLogger().info("Need to be executed as a player.");
			return;
		}

		// execute if all checks out
		argument.execute(sender, args);
	}

	public Map<String, CommandInformation> getCommands() {
		return gangland.getInitializer().getInformationManager().getCommands();
	}

	public CommandInformation getCommandInformation(String info) {
		return getCommands().get(info);
	}

}
