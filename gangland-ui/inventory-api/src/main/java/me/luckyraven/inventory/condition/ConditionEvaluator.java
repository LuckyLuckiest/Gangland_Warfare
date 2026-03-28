package me.luckyraven.inventory.condition;

import org.bukkit.entity.Player;

/**
 * Interface for evaluating conditions on slots
 */
public interface ConditionEvaluator {

	/**
	 * Evaluates a condition for a player
	 *
	 * @param player the player to evaluate against
	 * @param expression the condition expression
	 *
	 * @return true if the condition is met
	 */
	boolean evaluate(Player player, String expression);

}
