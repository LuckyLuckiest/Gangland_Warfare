package org.luckyraven.gangland.turf.capture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.capture.CaptureEligibility.Reason;
import org.luckyraven.gangland.turf.capture.CaptureRules.Window;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The W2-R1 capture window (D1): OWNERS_ONLINE attacks while a qualifying owner is online and for the logout window
 * after the last one leaves; a wake-up shield follows a closed-window login; OWNERS_OFFLINE keeps the pre-0.17.0 rule.
 * Offsets are relative to {@code NOW}: a {@code last} offset of 0 means "online now", {@code NEVER} means no stamp.
 */
@DisplayName("CaptureEligibility - OWNERS_ONLINE window, wake-up shield and the logout-window edge (D1)")
class CaptureEligibilityWindowTest {

	private static final long NOW       = 100_000_000L;
	private static final long W         = 600_000L;     // Post_Logoff_Protection_Minutes = 10
	private static final long K         = 120_000L;     // Wake_Up_Shield_Seconds = 120
	private static final long NEVER     = Long.MIN_VALUE;
	private static final long NO_UNTIL  = Long.MIN_VALUE;
	private static final int  OWNER_ID  = 7;

	private static CaptureEligibility eligibility(Window window, int wakeSeconds, long lastOffset, long wokeOffset) {
		Gang owner = CaptureFixtures.gang(OWNER_ID);
		when(owner.getLastMemberOnlineAt()).thenReturn(lastOffset == NEVER ? 0L : NOW - lastOffset);
		FakeGangLookup gangs = new FakeGangLookup();
		gangs.register(owner);
		CaptureRules rules = new CaptureRules(window, wakeSeconds, 24, 15, 60, 10, 4);
		long woke = wokeOffset == NEVER ? 0L : NOW - wokeOffset;
		return new CaptureEligibility(gangs, CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90),
		                              () -> rules, id -> id == OWNER_ID ? woke : 0L, id -> 99, id -> 0);
	}

	private static Turf turf(Integer owner, long lastCaptureOffset) {
		long lastCapture = lastCaptureOffset == NEVER ? 0L : NOW - lastCaptureOffset;
		return new Turf(1, "Docks", new CuboidRegion("world", 0, 0, 10, 10), owner, BigDecimal.TEN, 0L, lastCapture);
	}

	static Stream<Arguments> windowRows() {
		return Stream.of(
				//        name                       window                 wake  last       woke       owner    lastCap  reason                       until
				Arguments.of("online_now",           Window.OWNERS_ONLINE,  120,  0L,        NEVER,     OWNER_ID, NEVER,  Reason.ATTACKABLE,           NO_UNTIL),
				Arguments.of("online_window_edge",   Window.OWNERS_ONLINE,  120,  W,         NEVER,     OWNER_ID, NEVER,  Reason.ATTACKABLE,           NO_UNTIL),
				Arguments.of("online_past_window",   Window.OWNERS_ONLINE,  120,  W + 1,     NEVER,     OWNER_ID, NEVER,  Reason.NO_OWNERS_ONLINE,     NO_UNTIL),
				Arguments.of("online_never_stamped", Window.OWNERS_ONLINE,  120,  NEVER,     NEVER,     OWNER_ID, NEVER,  Reason.NO_OWNERS_ONLINE,     NO_UNTIL),
				Arguments.of("wake_up_running",      Window.OWNERS_ONLINE,  120,  0L,        10_000L,   OWNER_ID, NEVER,  Reason.WAKING_UP,            -10_000L + K),
				Arguments.of("wake_up_shield_edge",  Window.OWNERS_ONLINE,  120,  0L,        K,         OWNER_ID, NEVER,  Reason.ATTACKABLE,           NO_UNTIL),
				Arguments.of("wake_up_disabled",     Window.OWNERS_ONLINE,  0,    0L,        10_000L,   OWNER_ID, NEVER,  Reason.ATTACKABLE,           NO_UNTIL),
				Arguments.of("offline_owner_recent", Window.OWNERS_OFFLINE, 120,  0L,        10_000L,   OWNER_ID, NEVER,  Reason.OWNERS_ACTIVE,        W),
				Arguments.of("offline_past_window",  Window.OWNERS_OFFLINE, 120,  W + 1,     NEVER,     OWNER_ID, NEVER,  Reason.ATTACKABLE,           NO_UNTIL),
				Arguments.of("online_cooldown",      Window.OWNERS_ONLINE,  120,  0L,        NEVER,     OWNER_ID, 60_000L, Reason.COOLDOWN,            -60_000L + 900_000L),
				Arguments.of("unclaimed",            Window.OWNERS_ONLINE,  120,  NEVER,     NEVER,     null,     NEVER,  Reason.ATTACKABLE,           NO_UNTIL)
		);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("windowRows")
	@DisplayName("evaluate: the verdict for each window, shield and cooldown row")
	void evaluate_table(String name, Window window, int wakeSeconds, long lastOffset, long wokeOffset, Integer owner,
	                    long lastCaptureOffset, Reason reason, long untilOffset) {
		CaptureEligibility eligibility = eligibility(window, wakeSeconds, lastOffset, wokeOffset);

		CaptureEligibility.Verdict verdict = eligibility.evaluate(turf(owner, lastCaptureOffset), NOW);

		assertEquals(reason, verdict.reason(), name);
		long expectedUntil = untilOffset == NO_UNTIL ? 0L : NOW + untilOffset;
		assertEquals(expectedUntil, verdict.untilMillis(), name);
	}

	@Test
	@DisplayName("OWNERS_ONLINE: the window never closes sooner than two minutes (heartbeat is once a minute)")
	void onlineWindow_isAtLeastTwoMinutes() {
		Gang owner = CaptureFixtures.gang(OWNER_ID);
		when(owner.getLastMemberOnlineAt()).thenReturn(NOW - 90_000L);
		FakeGangLookup gangs = new FakeGangLookup();
		gangs.register(owner);
		CaptureRules rules = new CaptureRules(Window.OWNERS_ONLINE, 0, 24, 15, 60, 10, 4);
		CaptureEligibility eligibility = new CaptureEligibility(gangs,
		                                                        CaptureFixtures.settings(180, 15, 15, 1,
		                                                                                 new int[]{25}, 90, 90),
		                                                        () -> rules, id -> 0L, id -> 99, id -> 0);

		CaptureEligibility.Verdict verdict = eligibility.evaluate(turf(OWNER_ID, NEVER), NOW);

		assertEquals(Reason.ATTACKABLE, verdict.reason());
	}
}
