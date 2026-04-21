package me.luckyraven.command.argument.types;

import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.ArgumentLock;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class DoubleArgument extends Argument {

	private final ArgumentLock lock = new ArgumentLock();

	public DoubleArgument(JavaPlugin plugin, String argument, Tree<Argument> tree) {
		this(plugin, new String[]{argument}, tree, null);
	}

	public DoubleArgument(JavaPlugin plugin, String[] arguments, Tree<Argument> tree,
	                      TriConsumer<Argument, CommandSender, String[]> action) {
		this(plugin, arguments, tree, action, "");
	}

	public DoubleArgument(JavaPlugin plugin, String argument, Tree<Argument> tree,
	                      TriConsumer<Argument, CommandSender, String[]> action, String permission) {
		this(plugin, new String[]{argument}, tree, action, permission);
	}

	public DoubleArgument(JavaPlugin plugin, String[] arguments, Tree<Argument> tree,
	                      TriConsumer<Argument, CommandSender, String[]> action, String permission) {
		super(plugin, arguments, tree, action, permission);
	}

	@Override
	public void executeArgument(CommandSender sender, String[] args) {
		if (!lock.isLocked(sender)) {
			sender.sendMessage(Messages.ARGUMENT_CONFIRM_HINT.toString());
			lock.lock(sender);
			return;
		}

		lock.unlock(sender);

		super.executeArgument(sender, args);
	}

}
