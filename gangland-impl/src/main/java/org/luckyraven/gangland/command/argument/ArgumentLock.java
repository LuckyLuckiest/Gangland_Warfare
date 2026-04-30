package org.luckyraven.gangland.command.argument;

import org.bukkit.command.CommandSender;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Tracks per-sender lock state for arguments that require a confirmation step before execution.
 */
public class ArgumentLock {

	private final Set<CommandSender> lockedSenders = new HashSet<>();

	/**
	 * Locks the given sender and runs each phase in order.
	 *
	 * @param sender the command sender to lock
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
	 * Locks the given sender.
	 *
	 * @param sender the command sender to lock
	 */
	public void lock(CommandSender sender) {
		lockedSenders.add(sender);
	}

	/**
	 * Removes the lock for the given sender.
	 *
	 * @param sender the command sender to unlock
	 */
	public void unlock(CommandSender sender) {
		lockedSenders.remove(sender);
	}

	/**
	 * Returns whether the given sender has an active lock.
	 *
	 * @param sender the command sender to check
	 *
	 * @return true if the sender is currently locked
	 */
	public boolean isLocked(CommandSender sender) {
		return lockedSenders.contains(sender);
	}

}
