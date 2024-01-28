package me.luckyraven.command;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.HelpInfo;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
public abstract class CommandHandler {

	private final String         label;
	private final Set<String>    alias;
	private final String         permission;
	private final boolean        user;
	private final HelpInfo       helpInfo;
	private final Argument       argument;
	private final Tree<Argument> argumentTree;
	private final Gangland       gangland;

	public CommandHandler(Gangland gangland, String label, boolean user, String... alias) {
		this.gangland = gangland;
		this.label    = label.toLowerCase();

		this.alias = new HashSet<>();
		for (String s : alias) this.alias.add(s.toLowerCase());

		this.permission = "gangland.command." + label;

		this.user         = user;
		this.helpInfo     = new HelpInfo();
		this.argumentTree = new Tree<>();

		String[] args = new String[alias.length + 1];
		args[0] = label;
		System.arraycopy(alias, 0, args, 1, args.length - 1);

		this.argument = new Argument(args, argumentTree, this::onExecute, this.permission);
		this.argumentTree.add(argument.getNode());

		initializeArguments();
	}

	protected abstract void onExecute(Argument argument, CommandSender commandSender, String[] arguments);

	protected abstract void initializeArguments();

	protected abstract void help(CommandSender sender, int page);

	public void runExecute(CommandSender sender, String[] args) {
		// sender has the permission
		if (!sender.hasPermission(permission)) {
			sender.sendMessage(MessageAddon.COMMAND_NO_PERM.toString());
			return;
		}

		// check if the user should be a Player
		if (user && !(sender instanceof Player)) {
			sender.sendMessage(MessageAddon.NOT_PLAYER.toString());
			return;
		}

		// execute if all checks out
		argument.execute(sender, args);
	}

	public CommandInformation getCommandInformation(String info) {
		return getCommands().get(info);
	}

	public Map<String, CommandInformation> getCommands() {
		return gangland.getInitializer().getInformationManager().getCommands();
	}

}
