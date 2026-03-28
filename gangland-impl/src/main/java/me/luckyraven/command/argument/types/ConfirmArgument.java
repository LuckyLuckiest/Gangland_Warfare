package me.luckyraven.command.argument.types;

import me.luckyraven.command.argument.Argument;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class ConfirmArgument extends Argument {

	private final Set<CommandSender> lockedSenders = new HashSet<>();

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
		lockedSenders.add(sender);
		for (Consumer<CommandSender> phase : phases) {
			phase.accept(sender);
		}
	}

	/**
	 * Unlocks the 'confirm' for the given sender, removing their pending session.
	 *
	 * @param sender the command sender to unlock
	 */
	public void unlock(CommandSender sender) {
		lockedSenders.remove(sender);
	}

	/**
	 * Returns whether the given sender has an active 'confirm' session locked.
	 *
	 * @param sender the command sender to check
	 *
	 * @return true if the sender has a pending 'confirm'
	 */
	public boolean isLocked(CommandSender sender) {
		return lockedSenders.contains(sender);
	}

	@Override
	public void executeArgument(CommandSender sender, String[] args) {
		if (!isLocked(sender)) {
			sender.sendMessage(
					GanglandChatUtil.errorMessage("Need to execute the initial statement to use this argument."));
			return;
		}

		unlock(sender);

		super.executeArgument(sender, args);
	}

}
