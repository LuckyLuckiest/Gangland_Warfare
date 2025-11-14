package me.luckyraven.weapon;

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

}
