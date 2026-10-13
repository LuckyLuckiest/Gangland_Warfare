package org.luckyraven.gangland.npcshops.holdup;

import org.luckyraven.keystone.persistence.FileHandler;

/**
 * The {@code Hold_Up:} block of {@code npc/trader_settings.yml}. TDD skeleton: behaviour lands in the green wave.
 */
public record HoldUpSettings(boolean enable, double range, double aimAngle, int fillSeconds, int alarmSeconds,
                             double dislikeMood, int alarmSecondsDisliked, double takeMin, double takeMax,
                             int cooldownSeconds, String filling, String done, String aborted, String closed) {

	public static HoldUpSettings defaults() {
		throw new UnsupportedOperationException("TDD stub");
	}

	public static HoldUpSettings load(FileHandler fileHandler) {
		throw new UnsupportedOperationException("TDD stub");
	}

}
