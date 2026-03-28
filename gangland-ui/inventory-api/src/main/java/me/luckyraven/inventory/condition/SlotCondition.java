package me.luckyraven.inventory.condition;

import org.bukkit.entity.Player;

public record SlotCondition(String valueExpression) {

	/**
	 * Evaluates if this condition is met for the given player
	 */
	public boolean evaluate(Player player, ConditionEvaluator evaluator) {
		return evaluator.evaluate(player, valueExpression);
	}

}
