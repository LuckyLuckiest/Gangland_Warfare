package org.luckyraven.gangland.weapon;

import java.util.Set;

public enum SelectiveFire {

	AUTO,
	BURST,
	SINGLE;

	private static final SelectiveFire[] STATES = values();

	public static SelectiveFire getType(String type) {
		return switch (type.toLowerCase()) {
			case "single" -> SINGLE;
			case "burst" -> BURST;
			default -> AUTO;
		};
	}

	public static SelectiveFire getState(int index) {
		return STATES[index];
	}

	public SelectiveFire getNextState() {
		int next = this.ordinal() + 1;
		return next >= STATES.length ? getState(0) // loop to the beginning
		                             : getState(next); // next state
	}

	/**
	 * Cycles to the next state in the fixed enum order (AUTO → BURST → SINGLE → AUTO), skipping any state not present
	 * in {@code allowed}. Used by the per-weapon {@code Allowed_Modes} yml feature so weapons can restrict which fire
	 * modes they support without changing the global cycle order.
	 *
	 * @param allowed the modes this weapon is permitted to cycle through. If {@code null} or empty, this method
	 * 		returns {@link #getNextState()} (no restriction).
	 *
	 * @return the next allowed state, or {@code this} if {@code allowed} contains only one mode
	 */
	public SelectiveFire getNextState(Set<SelectiveFire> allowed) {
		if (allowed == null || allowed.isEmpty()) return getNextState();

		SelectiveFire candidate = this;
		for (int i = 0; i < STATES.length; i++) {
			candidate = candidate.getNextState();
			if (allowed.contains(candidate)) return candidate;
		}
		return this;
	}

}
