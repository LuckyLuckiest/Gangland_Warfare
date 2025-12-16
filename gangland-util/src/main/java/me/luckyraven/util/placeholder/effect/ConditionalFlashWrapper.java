package me.luckyraven.util.placeholder.effect;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds conditional flash effects to placeholders.
 * </br>
 * Usage: %flashif:&ltcondition&gt:&ltflash_on&gt:&ltflash_off&gt:&ltplaceholder&gt%
 * </br>
 * Examples:
 * </br>
 * - %flashif:user_wanted>0:5:5:user_wanted% - Flash wanted level if > 0
 * </br>
 * - %flashif:user_balance<1000:10:10:user_balance% - Flash balance if < 1000
 * </br>
 * - %flashif:gang_bounty>0:user_bounty% - Flash bounty if gang has bounty (default timing)
 * </br>
 * Supported operators: >, <, >=, <=, ==, !=
 */
public class ConditionalFlashWrapper {

	private static final Pattern CONDITIONAL_FLASH_PATTERN =
			Pattern.compile("flashif:([^:]+)(>|<|>=|<=|==|!=)([^:]+)(?::(\\d+):(\\d+))?:(.+)");

	/**
	 * Check if a parameter contains a conditional flash directive
	 */
	public static boolean isConditionalFlash(@NotNull String parameter) {
		return parameter.startsWith("flashif:");
	}

	/**
	 * Process a conditional flash placeholder
	 *
	 * @param parameter The full parameter (e.g., "flashif:user_wanted>0:5:5:user_wanted")
	 * @param resolver Function to resolve the inner placeholder
	 * @param player The player context
	 *
	 * @return The flashed result or the normal result if condition not met
	 */
	@Nullable
	public static String processConditionalFlash(@NotNull String parameter,
												 FlashPlaceholderWrapper.PlaceholderResolver resolver,
												 @Nullable OfflinePlayer player) {
		Matcher matcher = CONDITIONAL_FLASH_PATTERN.matcher(parameter);

		if (!matcher.matches()) {
			return null;
		}

		String conditionPlaceholder = matcher.group(1).trim();
		String operator             = matcher.group(2);
		String conditionValue       = matcher.group(3).trim();
		String flashOn              = matcher.group(4);
		String flashOff             = matcher.group(5);
		String contentPlaceholder   = matcher.group(6).trim();

		// Resolve the condition placeholder
		String resolvedCondition = resolver.resolve(player, conditionPlaceholder);

		if (resolvedCondition == null || resolvedCondition.equals("NA")) {
			// Condition can't be evaluated, return content without flash
			return resolver.resolve(player, contentPlaceholder);
		}

		// Evaluate the condition
		boolean conditionMet = evaluateCondition(resolvedCondition, operator, conditionValue);

		if (!conditionMet) {
			// Condition not met, return normal content without flash
			return resolver.resolve(player, contentPlaceholder);
		}

		// Condition met, apply flash effect
		String resolvedContent = resolver.resolve(player, contentPlaceholder);

		if (resolvedContent == null || resolvedContent.equals("NA")) {
			return resolvedContent;
		}

		// Build flash placeholder string
		String flashParam;
		if (flashOn != null && flashOff != null) {
			flashParam = "flash:" + flashOn + ":" + flashOff + ":" + contentPlaceholder;
		} else {
			flashParam = "flash:" + contentPlaceholder;
		}

		// Process through regular flash
		return FlashPlaceholderWrapper.processFlash(flashParam, resolver, player);
	}

	/**
	 * Evaluate a condition between two values
	 *
	 * @param leftValue The left side of the comparison (resolved placeholder)
	 * @param operator The comparison operator (>, <, >=, <=, ==, !=)
	 * @param rightValue The right side of the comparison (static value)
	 *
	 * @return true if condition is met, false otherwise
	 */
	private static boolean evaluateCondition(String leftValue, String operator, String rightValue) {
		try {
			// Try numeric comparison first
			double left  = parseNumber(leftValue);
			double right = parseNumber(rightValue);

			return switch (operator) {
				case ">" -> left > right;
				case "<" -> left < right;
				case ">=" -> left >= right;
				case "<=" -> left <= right;
				case "==" -> Math.abs(left - right) < 0.0001;
				case "!=" -> Math.abs(left - right) >= 0.0001;
				default -> false;
			};
		} catch (NumberFormatException e) {
			// If not numeric, try string comparison
			return switch (operator) {
				case "==" -> leftValue.equalsIgnoreCase(rightValue);
				case "!=" -> !leftValue.equalsIgnoreCase(rightValue);
				default -> false;
			};
		}
	}

	/**
	 * Parse a number from a string, handling formatted numbers (e.g., "1,234.56", "$1000")
	 *
	 * @param value The string to parse
	 *
	 * @return The parsed number
	 *
	 * @throws NumberFormatException if the string is not a valid number
	 */
	private static double parseNumber(String value) throws NumberFormatException {
		// Remove common formatting characters
		String cleaned = value.replaceAll("[,$€£¥%\\s]", "")
							  .replace("true", "1")
							  .replace("false", "0");

		return Double.parseDouble(cleaned);
	}

}
