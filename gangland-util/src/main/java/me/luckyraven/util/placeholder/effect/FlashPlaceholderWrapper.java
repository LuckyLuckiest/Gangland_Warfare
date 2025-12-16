package me.luckyraven.util.placeholder.effect;

import lombok.Setter;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to add flash effects to placeholders dynamically. Usage: %flash:<duration_on>:<duration_off>:<placeholder>%
 * Example: %flash:10:10:user_balance% - flashes the balance placeholder Example: %flash:gang_name% - uses default
 * timing (10 ticks on, 10 ticks off)
 */
public class FlashPlaceholderWrapper {

	private static final Pattern FLASH_PATTERN = Pattern.compile("flash(?::(\\d+):(\\d+))?:(.+)");

	@Setter
	private static long currentTick = 0;

	/**
	 * Update the current tick counter (call this from scoreboard update)
	 */
	public static void updateTick() {
		currentTick++;
	}

	/**
	 * Check if a parameter contains a flash directive
	 */
	public static boolean isFlashPlaceholder(@NotNull String parameter) {
		return parameter.startsWith("flash:");
	}

	/**
	 * Process a flash placeholder
	 *
	 * @param parameter The full parameter (e.g., "flash:10:10:user_balance")
	 * @param resolver Function to resolve the inner placeholder
	 *
	 * @return The flashed result or null if not a flash placeholder
	 */
	@Nullable
	public static String processFlash(@NotNull String parameter, PlaceholderResolver resolver,
									  @Nullable OfflinePlayer player) {
		Matcher matcher = FLASH_PATTERN.matcher(parameter);

		if (!matcher.matches()) {
			return null;
		}

		String flashOn          = matcher.group(1);
		String flashOff         = matcher.group(2);
		String innerPlaceholder = matcher.group(3);

		// Resolve the inner placeholder first
		String resolvedContent = resolver.resolve(player, innerPlaceholder);

		if (resolvedContent == null || resolvedContent.equals("NA")) {
			return resolvedContent; // Don't flash null or NA values
		}

		// Create flash effect
		FlashEffect effect;
		if (flashOn != null && flashOff != null) {
			long on  = Long.parseLong(flashOn);
			long off = Long.parseLong(flashOff);
			effect = new FlashEffect(innerPlaceholder, on, off);
		} else {
			effect = new FlashEffect(innerPlaceholder);
		}

		// Apply flash effect with fade for smoother GTA-like appearance
		return effect.applyWithFade(resolvedContent, currentTick);
	}

	/**
	 * Functional interface for resolving placeholders
	 */
	@FunctionalInterface
	public interface PlaceholderResolver {

		@Nullable String resolve(@Nullable OfflinePlayer player, @NotNull String parameter);

	}

}
