package me.luckyraven.inventory.condition;

import lombok.RequiredArgsConstructor;
import me.luckyraven.util.Placeholder;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public class BooleanExpressionEvaluator implements ConditionEvaluator {

	private final Placeholder placeholder;

	/**
	 * Evaluates a placeholder string as a boolean expression
	 *
	 * @param player the player context
	 * @param expression the expression with placeholders
	 *
	 * @return true if the expression evaluates to true
	 */
	public boolean evaluate(Player player, String expression) {
		// Convert placeholders
		String converted = placeholder.convert(player, expression);

		// Normalize the string
		converted = converted.trim().toLowerCase();

		// Parse as boolean
		return parseBoolean(converted);
	}

	private boolean parseBoolean(String value) {
		return switch (value.toLowerCase()) {
			case "true", "yes", "1" -> true;
			case "false", "no", "0", "na" -> false;
			default -> {
				try {
					double num = Double.parseDouble(value);
					yield num != 0.0;
				} catch (NumberFormatException e) {
					yield !value.isEmpty();
				}
			}
		};
	}

}
