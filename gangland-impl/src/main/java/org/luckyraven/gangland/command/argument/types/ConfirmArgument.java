package org.luckyraven.gangland.command.argument.types;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.ArgumentLock;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;

import java.util.function.Consumer;

public class ConfirmArgument extends Argument {

	private final ArgumentLock lock = new ArgumentLock();

	public ConfirmArgument(JavaPlugin plugin, Tree<Argument> tree) {
		this(plugin, tree, null);
	}

	public ConfirmArgument(JavaPlugin plugin, Tree<Argument> tree,
	                       TriConsumer<Argument, CommandSender, String[]> action) {
		super(plugin, "confirm", tree, action);
	}

	/**
	 * Locks the 'confirm' for the given sender and runs each phase in order. Phases are consumers that execute setup
	 * logic tied to this sender's confirmation session, such as starting a countdown timer.
	 *
	 * @param sender the command sender to lock confirm for
	 * @param phases ordered setup actions to run for this sender's session
	 */
	@SafeVarargs
	public final void lock(CommandSender sender, Consumer<CommandSender>... phases) {
		lock.lock(sender, phases);
	}

	/**
	 * Unlocks the 'confirm' for the given sender, removing their pending session.
	 *
	 * @param sender the command sender to unlock
	 */
	public void unlock(CommandSender sender) {
		lock.unlock(sender);
	}

	/**
	 * Returns whether the given sender has an active 'confirm' session locked.
	 *
	 * @param sender the command sender to check
	 *
	 * @return true if the sender has a pending 'confirm'
	 */
	public boolean isLocked(CommandSender sender) {
		return lock.isLocked(sender);
	}

	@Override
	public void executeArgument(CommandSender sender, String[] args) {
		if (!lock.isLocked(sender)) {
			sender.sendMessage(Messages.ARGUMENT_CONFIRM_REQUIRED.toString());
			return;
		}

		lock.unlock(sender);

		super.executeArgument(sender, args);
	}

}
