package me.luckyraven.util.placeholder.effect;

import me.luckyraven.util.utilities.ChatUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates a GTA-style flash effect for placeholders. The effect alternates between showing and hiding the content.
 */
public class FlashEffect {

	private static final Map<String, FlashState> flashStates = new HashMap<>();

	private final String key;
	private final long   flashDuration;
	private final long   hideDuration;

	/**
	 * Creates a flash effect with custom timing
	 *
	 * @param key Unique identifier for this flash effect
	 * @param flashDuration How long the content is visible (in ticks)
	 * @param hideDuration How long the content is hidden (in ticks)
	 */
	public FlashEffect(String key, long flashDuration, long hideDuration) {
		this.key           = key;
		this.flashDuration = flashDuration;
		this.hideDuration  = hideDuration;

		flashStates.putIfAbsent(key, new FlashState());
	}

	/**
	 * Creates a flash effect with default GTA-like timing (10 ticks on, 10 ticks off)
	 *
	 * @param key Unique identifier for this flash effect
	 */
	public FlashEffect(String key) {
		this(key, 10L, 10L);
	}

	/**
	 * Clear all flash states
	 */
	public static void clearAll() {
		flashStates.clear();
	}

	/**
	 * Apply the flash effect to the given content
	 *
	 * @param content The content to flash
	 * @param currentTick Current game tick (use scoreboard update tick)
	 *
	 * @return The content if visible, empty string if hidden
	 */
	public String apply(String content, long currentTick) {
		FlashState state = flashStates.get(key);

		if (state.lastUpdate == 0) {
			state.lastUpdate = currentTick;
			state.visible    = true;
		}

		long elapsed       = currentTick - state.lastUpdate;
		long cycleDuration = flashDuration + hideDuration;
		long position      = elapsed % cycleDuration;

		state.visible = position < flashDuration;

		return state.visible ? content : "";
	}

	/**
	 * Apply the flash effect with fade-in/fade-out using color codes Creates a smoother GTA-style effect
	 */
	public String applyWithFade(String content, long currentTick) {
		FlashState state = flashStates.get(key);

		if (state.lastUpdate == 0) {
			state.lastUpdate = currentTick;
			state.visible    = true;
		}

		long elapsed       = currentTick - state.lastUpdate;
		long cycleDuration = flashDuration + hideDuration;
		long position      = elapsed % cycleDuration;

		if (position < flashDuration) {
			// Visible phase - apply brightness fade
			long fadeIn  = Math.min(position, 3); // Fade in over 3 ticks
			long fadeOut = Math.min(flashDuration - position, 3); // Fade out in last 3 ticks
			long fade    = Math.min(fadeIn, fadeOut);

			return applyBrightness(content, fade);
		} else {
			// Hidden phase
			return "";
		}
	}

	/**
	 * Reset the flash state for this effect
	 */
	public void reset() {
		flashStates.remove(key);
	}

	private String applyBrightness(String content, long brightness) {
		// Apply color intensity based on brightness (0-3)
		// This creates a smooth fade effect
		return switch ((int) brightness) {
			case 0 -> ChatUtil.color("&8" + content);
			case 1 -> ChatUtil.color("&7" + content);
			case 2 -> ChatUtil.color("&f" + content);
			default -> content; // Full brightness
		};
	}

	private static class FlashState {
		long    lastUpdate = 0;
		boolean visible    = true;
	}
}
