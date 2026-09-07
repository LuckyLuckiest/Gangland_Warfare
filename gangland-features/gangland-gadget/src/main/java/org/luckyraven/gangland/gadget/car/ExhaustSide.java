package org.luckyraven.gangland.gadget.car;

import java.util.Random;

/**
 * Determines which side of a car's rear the exhaust pipe is located. Assigned randomly on first mount and persisted in
 * the car item's NBT so it never changes between sessions.
 */
public enum ExhaustSide {

	LEFT,
	RIGHT,
	BOTH;

	public static ExhaustSide random() {
		ExhaustSide[] values = values();
		return values[new Random().nextInt(values.length)];
	}

	public static ExhaustSide fromString(String value) {
		try {
			return valueOf(value);
		} catch (IllegalArgumentException e) {
			return random();
		}
	}
}
