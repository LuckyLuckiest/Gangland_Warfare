package me.luckyraven.bukkit.sign.aspect;

import me.luckyraven.bukkit.sign.model.ParsedSign;
import org.bukkit.entity.Player;

/**
 * Represents a modular behavior component that can be attached to sign types. Aspects are executed in order when a sign
 * is interacted with.
 */
public interface SignAspect {

	/**
	 * Execute this aspect's behavior
	 *
	 * @param player The player interacting with the sign
	 * @param sign The parsed sign data
	 *
	 * @return Result of the aspect execution
	 */
	AspectResult execute(Player player, ParsedSign sign);

	/**
	 * Check if this aspect can execute (preconditions)
	 *
	 * @param player The player interacting
	 * @param sign The parsed sign
	 *
	 * @return true if aspect can execute
	 */
	boolean canExecute(Player player, ParsedSign sign);

	/**
	 * Name of this aspect for logging/debugging
	 */
	String getName();

	/**
	 * Priority for execution order (higher = executed first)
	 */
	default int getPriority() {
		return 0;
	}

}
