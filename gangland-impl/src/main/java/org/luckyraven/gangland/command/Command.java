package org.luckyraven.gangland.command;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.data.CommandInformation;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
public abstract class Command {

	/**
	 * Static binding to the singleton {@link InformationManager}, set once by {@code GanglandContext.runCommandPhase()}
	 * before commands are scanned. Subclasses use {@link #getCommandInformation(String)} / {@link #getCommands()}
	 * during their constructors to build help info — passing the manager through every subclass {@code super(...)} call
	 * would touch 25+ files for one read, so a single binding is used here. The setter is package-private (via
	 * {@link Setter} default access) so only command-package code can rebind it.
	 */
	@Setter(value = AccessLevel.PUBLIC)
	private static InformationManager informationManager;

	@Getter(value = AccessLevel.PROTECTED)
	private final Gangland       gangland;
	private final String         label;
	private final Set<String>    alias;
	private final String         permission;
	private final boolean        user;
	private final HelpInfo       helpInfo;
	private final Argument       argument;
	private final Tree<Argument> argumentTree;

	public Command(Gangland gangland, String label, boolean user, String... alias) {
		this.gangland = gangland;
		this.label    = label.toLowerCase();

		this.alias = new HashSet<>();
		for (String s : alias) this.alias.add(s.toLowerCase());

		this.permission = String.format("%s.command.%s", Gangland.FULL_PREFIX.toLowerCase(), label);

		this.user         = user;
		this.helpInfo     = new HelpInfo();
		this.argumentTree = new Tree<>();

		String[] args = new String[alias.length + 1];
		args[0] = label;
		System.arraycopy(alias, 0, args, 1, args.length - 1);

		this.argument = new Argument(gangland, args, argumentTree, this::onExecute, this.permission);
		this.argumentTree.add(argument.getNode());

		// initializeArguments() is invoked by CommandManager.createInstance() AFTER the subclass constructor
		// finishes — this delay is required because subclass fields (constructor-injected dependencies) aren't
		// assigned until after super() returns, and many subclasses pass those fields to sub-arguments.
	}

	protected abstract void onExecute(Argument argument, CommandSender commandSender, String[] arguments);

	/**
	 * Build and attach sub-arguments to this command. Called by {@code CommandManager.createInstance()} once the
	 * subclass constructor has fully run, so subclass fields (constructor-injected beans) are guaranteed non-null.
	 */
	protected abstract void initializeArguments();

	protected abstract void help(CommandSender sender, int page);

	public void runExecute(CommandSender sender, String[] args) {
		// sender has the permission
		if (!sender.hasPermission(permission)) {
			sender.sendMessage(Messages.COMMAND_NO_PERM.toString());
			return;
		}

		// check if the user should be a Player
		if (user && !(sender instanceof Player)) {
			sender.sendMessage(Messages.NOT_PLAYER.toString());
			return;
		}

		// execute if all checks out
		argument.execute(Gangland.SHORT_PREFIX, sender, args);
	}

	public CommandInformation getCommandInformation(String info) {
		return getCommands().get(info);
	}

	public Map<String, CommandInformation> getCommands() {
		return informationManager.getCommands();
	}

}
