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
	private final long   ticksPerState;

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
		this.ticksPerState = Math.max(1, flashDuration / Brightness.getVisibleStates().length);

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

		long cycleDuration = flashDuration + hideDuration;
		long position      = currentTick % cycleDuration;

		return position < flashDuration ? content : "";
	}

	/**
	 * Apply the flash effect with fade-in/fade-out using brightness states Creates a smoother GTA-style effect with
	 * sequential state transitions
	 */
	public String applyWithFade(String content, long currentTick) {
		FlashState state = flashStates.get(key);

		long cycleDuration = flashDuration + hideDuration;
		long position      = currentTick % cycleDuration;

		if (position < flashDuration) {
			// Visible phase - cycle through brightness states
			state.updateBrightness(position, ticksPerState);
			return applyBrightness(content, state.currentBrightness);
		} else {
			// Hidden phase
			state.currentBrightness = Brightness.HIDDEN;
			return "";
		}
	}

	/**
	 * Reset the flash state for this effect
	 */
	public void reset() {
		flashStates.remove(key);
	}

	private String applyBrightness(String content, Brightness brightness) {
		return switch (brightness) {
			case HIDDEN -> "";
			case VERY_DIM -> ChatUtil.color("&8" + content);
			case DIM -> ChatUtil.color("&7" + content);
			case NORMAL -> ChatUtil.color("&f" + content);
			case FULL -> content;
		};
	}

	/**
	 * Brightness levels for fade effects - transitions sequentially
	 */
	private enum Brightness {
		HIDDEN,
		VERY_DIM,
		DIM,
		NORMAL,
		FULL;

		private static final Brightness[] VISIBLE_STATES = {VERY_DIM, DIM, NORMAL, FULL};

		/**
		 * Get visible states for calculating transitions
		 */
		public static Brightness[] getVisibleStates() {
			return VISIBLE_STATES;
		}

		/**
		 * Get brightness state by phase (0 = fade in, 1 = full, 2 = fade out)
		 */
		public static Brightness fromPhase(int stateIndex) {
			if (stateIndex < 0 || stateIndex >= VISIBLE_STATES.length) {
				return FULL;
			}
			return VISIBLE_STATES[stateIndex];
		}

		/**
		 * Get the next brightness state in sequence
		 */
		public Brightness next() {
			int currentIndex = getVisibleIndex();
			if (currentIndex == -1) return VERY_DIM; // Start from beginning

			int nextIndex = currentIndex + 1;
			return nextIndex >= VISIBLE_STATES.length ? FULL : VISIBLE_STATES[nextIndex];
		}

		/**
		 * Get the previous brightness state in sequence
		 */
		public Brightness previous() {
			int currentIndex = getVisibleIndex();
			if (currentIndex <= 0) return VERY_DIM;

			return VISIBLE_STATES[currentIndex - 1];
		}

		/**
		 * Get this state's index in the visible states array
		 */
		private int getVisibleIndex() {
			for (int i = 0; i < VISIBLE_STATES.length; i++) {
				if (VISIBLE_STATES[i] == this) return i;
			}
			return -1;
		}
	}

	private static class FlashState {
		Brightness currentBrightness = Brightness.VERY_DIM;
		long       lastTickUpdate    = -1;

		/**
		 * Update brightness based on position in the flash cycle Transitions through states sequentially for even
		 * animation
		 */
		void updateBrightness(long position, long ticksPerState) {
			// Determine which state we should be in based on position
			int totalStates = Brightness.getVisibleStates().length;

			// Calculate state index: cycles through 0,1,2,3,3,2,1,0 for smooth fade in/out
			long statePosition = position / ticksPerState;
			int  stateIndex;

			if (statePosition < totalStates) {
				// Fade in: 0 -> 1 -> 2 -> 3
				stateIndex = (int) statePosition;
			} else {
				// Fade out: 3 -> 2 -> 1 -> 0
				long fadeOutPosition = statePosition - totalStates;
				stateIndex = totalStates - 1 - (int) Math.min(fadeOutPosition, totalStates - 1);
			}

			currentBrightness = Brightness.fromPhase(stateIndex);
			lastTickUpdate    = position;
		}
	}
}
