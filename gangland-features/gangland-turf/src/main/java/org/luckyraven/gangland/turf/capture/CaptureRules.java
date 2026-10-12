package org.luckyraven.gangland.turf.capture;

/**
 * The 0.17.0 capture rules from turf/turf_rules.yml Capture: (W2). Immutable; TurfRules rebuilds it on every load, and
 * readers take it through a Supplier so a /glw reload is seen. A value of 0 turns that rule off.
 */
public record CaptureRules(Window window, int wakeUpShieldSeconds, int minMemberAgeHours, int presenceSeconds,
                           int minCaptureSeconds, int maxContestMinutes, int guardWeightCap) {

	/** When an owned turf can be attacked (D1). */
	public enum Window {
		/** Attackable while an owner is online and for the logout window after the last one leaves. */
		OWNERS_ONLINE,
		/** The pre-0.17.0 rule: attackable once every owner has been offline for the logout window. */
		OWNERS_OFFLINE
	}

	/** The shipped turf_rules.yml values. */
	public static final CaptureRules DEFAULTS = new CaptureRules(Window.OWNERS_ONLINE, 120, 24, 15, 60, 10, 4);

	/**
	 * The pre-0.17.0 behaviour (owners-offline window, no wake-up, presence, clamp, stall or guards). Only the
	 * convenience constructors use it, so tests written before W2 keep their meaning.
	 */
	public static final CaptureRules LEGACY = new CaptureRules(Window.OWNERS_OFFLINE, 0, 0, 0, 0, 0, 0);

}
