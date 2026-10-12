package org.luckyraven.gangland.turf.capture;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;
import org.luckyraven.gangland.turf.state.CapturePhase;
import org.luckyraven.gangland.turf.state.TurfState;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Lane W2-R2: the one capture engine's rules (0.17.0, TF-48, TF-11, TF-12, TF-26, TF-02 defence half). Plurality
 * start, presence, one bar for owned and unclaimed turfs, guards on the bar, the rate clamp, the stall, the grace
 * window and the cap re-check. Drives {@link CaptureService#tick} through {@link W2R2Harness} with a fake clock.
 */
@DisplayName("CaptureService - 0.17.0 capture rules (W2-R2)")
class CaptureServiceRulesTest {

	private static final long T0 = W2R2Harness.T0;

	/** Puts the turf into CONTESTING against {@code challengerId} with the given progress and last-seen time. */
	private static void contest(W2R2Harness h, int challengerId, double progress, long lastSeen) {
		TurfRuntimeState state = h.state();
		state.setState(TurfState.CONTESTING);
		state.setChallengerGangId(challengerId);
		state.setCaptureProgress(progress);
		state.setLastChallengerSeenAt(lastSeen);
	}

	private static TurfCaptureFailedEvent.Reason onlyFailure(W2R2Harness h) {
		List<TurfCaptureFailedEvent.Reason> reasons = h.failedReasons();
		assertEquals(1, reasons.size(), "exactly one failed event");
		return reasons.get(0);
	}

	@Test
	@DisplayName("TF-48: one alt of a third gang standing inside does not block a start by the largest attacker gang")
	void alt_doesNotBlockStart() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 4));
		h.gang(1);
		h.gang(2);
		h.gang(3);
		h.ownedBy(1);
		h.join(2);
		h.join(2);
		h.join(3);

		h.tick(T0);

		assertEquals(TurfState.CONTESTING, h.state().getState());
		assertEquals(2, h.state().getChallengerGangId());
	}

	@Test
	@DisplayName("TF-48: tied rival gangs never start a contest")
	void tiedRivals_doNotStart() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 4));
		h.gang(1);
		h.gang(2);
		h.gang(3);
		h.ownedBy(1);
		h.join(2);
		h.join(2);
		h.join(3);
		h.join(3);

		h.tick(T0);

		assertEquals(TurfState.IDLE, h.state().getState());
	}

	@Test
	@DisplayName("owned turf: a start needs strictly more counted attackers than the counted defenders")
	void mustOutnumberDefenders() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		h.join(1);
		h.join(2);

		h.tick(T0);
		assertEquals(TurfState.IDLE, h.state().getState(), "1 attacker vs 1 owner");

		h.join(2);
		h.tick(T0 + 1_000L);
		assertEquals(TurfState.CONTESTING, h.state().getState(), "2 attackers vs 1 owner");
	}

	@Test
	@DisplayName("guards never block a start: they are not counted while the turf is IDLE")
	void guardsDoNotBlockStart() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		h.guards.put(1, 3);
		h.join(2);

		h.tick(T0);

		assertEquals(TurfState.CONTESTING, h.state().getState());
		assertEquals(0, h.state().getGuardsCounted());
	}

	@Test
	@DisplayName("presence: an attacker counts only after Presence_Seconds (15) inside")
	void presence_attackerCountsAfterPresenceSeconds() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(15, 0, 0, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		h.join(2);

		h.tick(T0);
		h.tick(T0 + 5_000L);
		assertEquals(TurfState.IDLE, h.state().getState(), "5 s inside is not enough");

		h.tick(T0 + 15_000L);
		assertEquals(TurfState.CONTESTING, h.state().getState(), "15 s inside counts");
	}

	@Test
	@DisplayName("presence: dying restarts the attacker's count")
	void presence_deathRestartsTheCount() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(15, 0, 0, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		Player attacker = h.join(2);

		h.tick(T0);
		h.tick(T0 + 5_000L);
		h.setDead(attacker, true);
		h.tick(T0 + 10_000L);
		h.setDead(attacker, false);
		h.tick(T0 + 11_000L);
		h.tick(T0 + 20_000L);
		assertEquals(TurfState.IDLE, h.state().getState(), "9 s alive since the respawn is not enough");

		h.tick(T0 + 26_000L);
		assertEquals(TurfState.CONTESTING, h.state().getState(), "15 s alive since the respawn counts");
	}

	@Test
	@DisplayName("presence: a defender counts at once, on the very next tick")
	void presence_defendersCountAtOnce() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(15, 0, 0, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		contest(h, 2, 50.0, T0);

		h.join(1);
		h.tick(T0);

		assertEquals(1, h.state().getDefendersInside());
	}

	@Test
	@DisplayName("presence: an ally who abandons the alliance restarts his count as a rival")
	void relationChange_restartsCount() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(15, 0, 0, 4));
		Gang        owner = h.gang(1);
		h.gang(2);
		Gang ally = h.gang(5);
		when(ally.isAlly(owner)).thenReturn(true);
		h.ownedBy(1);
		contest(h, 2, 50.0, T0);
		h.join(5);
		h.join(2); // the challenger keeps a counted attacker, so the abandon grace does not reset the turf

		h.tick(T0);
		assertFalse(h.state().getChallengersInside().containsKey(5), "an ally is a defender, not a challenger");

		when(ally.isAlly(owner)).thenReturn(false);
		h.tick(T0 + 5_000L);
		assertFalse(h.state().getChallengersInside().containsKey(5), "the count restarts when the alliance ends");

		h.tick(T0 + 20_000L);
		assertEquals(1, h.state().getChallengersInside().get(5), "15 s after the change he counts as a rival");
	}

	@Test
	@DisplayName("TF-12: the rate never exceeds 100 / Min_Capture_Seconds")
	void minCapture_clampsTheRate() {
		W2R2Harness h = new W2R2Harness(180, W2R2Harness.rules(0, 60, 0, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		contest(h, 2, 0.0, T0);
		for (int i = 0; i < 10; i++) {
			h.join(2);
		}

		h.tick(T0);

		assertEquals(100.0 / 60, h.state().getCaptureProgress(), 1e-9);
	}

	@Test
	@DisplayName("TF-12: a contest running Max_Contest_Minutes (10 here: 1) ends STALLED")
	void stall_endsStalled() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 1, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		h.join(2);

		h.tick(T0);
		h.tick(T0 + 59_000L);
		assertEquals(TurfState.CONTESTING, h.state().getState(), "59 s is still inside the window");

		h.tick(T0 + 60_000L);
		assertEquals(TurfState.IDLE, h.state().getState());
		assertEquals(List.of(TurfCaptureFailedEvent.Reason.STALLED), h.failedReasons());
		TurfCaptureFailedEvent failed = (TurfCaptureFailedEvent) h.lastEvents.get(0);
		assertEquals(2, failed.getChallengerGangId());
	}

	@Test
	@DisplayName("TF-02 defence half: owned contest counts the guards as defenders, capped at Guard_Weight_Cap")
	void guards_countForDefenders_capped() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		contest(h, 2, 50.0, T0);
		h.guards.put(1, 2);
		h.join(2);

		h.tick(T0);

		assertEquals(45.0, h.state().getCaptureProgress(), 1e-9, "1 attacker vs 2 guards goes down by 100/duration");
		assertEquals(2, h.state().getGuardsCounted());
	}

	@Test
	@DisplayName("TF-02 defence half: guards beyond Guard_Weight_Cap do not count")
	void guards_capOfOneLimitsThem() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 1));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		contest(h, 2, 50.0, T0);
		h.guards.put(1, 2);
		h.join(2);

		h.tick(T0);

		assertEquals(50.0, h.state().getCaptureProgress(), 1e-9, "1 attacker vs 1 counted guard is level");
		assertEquals(1, h.state().getGuardsCounted());
	}

	@Test
	@DisplayName("TF-02 defence half: an unclaimed contest never counts guards")
	void guards_unclaimedNeverCount() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 4));
		h.gang(2);
		h.unclaimed();
		h.guards.put(1, 3);
		contest(h, 2, 50.0, T0);
		h.join(2);

		h.tick(T0);

		assertEquals(0, h.state().getGuardsCounted());
	}

	@Test
	@DisplayName("TF-12 start: a gang at its turf cap (OVER_CAP) cannot start a contest")
	void capAtStart_refuses() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		h.cap.put(2, 1);
		h.owned.put(2, 1);
		h.join(2);

		h.tick(T0);

		assertEquals(TurfState.IDLE, h.state().getState());
	}

	@Test
	@DisplayName("P3.5: a gang that reaches its cap during the contest is cancelled at 100, the owner is unchanged")
	void capAtComplete_cancels() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 4));
		Gang owner = h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		h.join(2);
		h.tick(T0);
		assertEquals(TurfState.CONTESTING, h.state().getState());

		h.owned.put(2, 1);
		h.cap.put(2, 1);
		h.state().setCaptureProgress(99.9);
		h.tick(T0 + 1_000L);

		assertEquals(owner.getId(), h.turf.getOwnerGangId());
		assertEquals(TurfState.IDLE, h.state().getState());
		assertEquals(List.of(TurfCaptureFailedEvent.Reason.CANCELLED), h.failedReasons());
	}

	@Test
	@DisplayName("TF-12 one bar: an unclaimed turf runs one bar at Capture.Duration_Seconds and stays in CLAIM")
	void unclaimed_oneBar() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 4));
		h.gang(2);
		h.gang(3);
		h.unclaimed();
		h.join(2);
		h.join(2);
		h.join(3);

		h.tick(T0);
		assertEquals(TurfState.CONTESTING, h.state().getState());
		assertEquals(2, h.state().getChallengerGangId());

		for (int second = 1; second <= 19; second++) {
			h.tick(T0 + second * 1_000L);
			assertEquals(CapturePhase.CLAIM, h.state().getPhase(), "phase stays CLAIM at every tick");
		}
		assertEquals(95.0, h.state().getCaptureProgress(), 1e-9, "+1 net = +5 per second on a 20 s bar");

		h.tick(T0 + 20_000L);

		assertEquals(2, h.turf.getOwnerGangId());
		assertEquals(TurfState.COOLDOWN, h.state().getState());
		assertTrue(h.lastEvents.stream().anyMatch(TurfCapturedEvent.class::isInstance));
	}

	@Test
	@DisplayName("TF-12 one bar: an unclaimed contest rolled back by a larger rival fails DEFENDED")
	void unclaimed_rolledBackByOthers_defended() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 4));
		h.gang(2);
		h.gang(3);
		h.unclaimed();
		contest(h, 2, 5.0, T0);
		h.join(2);
		h.join(3);
		h.join(3);

		h.tick(T0);

		assertEquals(TurfState.IDLE, h.state().getState());
		assertEquals(TurfCaptureFailedEvent.Reason.DEFENDED, onlyFailure(h));
	}

	@Test
	@DisplayName("TF-12 freeze: an empty owned contest holds its bar during the grace window, then ends ABANDONED")
	void emptyContest_freezesThenAbandons() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		contest(h, 2, 40.0, T0);

		h.tick(T0 + 10_000L);
		assertEquals(40.0, h.state().getCaptureProgress(), 1e-9, "nobody inside: the bar freezes");
		assertEquals(TurfState.CONTESTING, h.state().getState());

		h.tick(T0 + 16_000L);
		assertEquals(TurfState.IDLE, h.state().getState());
		assertEquals(TurfCaptureFailedEvent.Reason.ABANDONED, onlyFailure(h));
	}

	@Test
	@DisplayName("presence: challengersInside holds only counted attackers")
	void challengersInside_holdsCountedAttackers() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(15, 0, 0, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		h.join(2);

		h.tick(T0);
		h.tick(T0 + 5_000L);
		assertTrue(h.state().getChallengersInside().isEmpty(), "inside, but not yet counted");

		h.tick(T0 + 15_000L);
		assertEquals(Map.of(2, 1), h.state().getChallengersInside());
	}

	@Test
	@DisplayName("the start leaves the phase at CLAIM and the counted-phase fields clean")
	void startLeavesPhaseAtClaim() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		h.join(2);

		h.tick(T0);

		assertEquals(CapturePhase.CLAIM, h.state().getPhase());
		assertEquals(0, h.state().getGuardsCounted());
		assertEquals(T0, h.state().getContestStartedAt());
	}

	@Test
	@DisplayName("guards held at 0%: an owned contest with 3 guards and 1 attacker stays CONTESTING across ticks")
	void guardHeldBar_doesNotEndDefendedAtZero() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		h.guards.put(1, 3);
		h.join(2);

		h.tick(T0);
		h.tick(T0 + 1_000L);

		assertEquals(TurfState.CONTESTING, h.state().getState());
		assertEquals(0.0, h.state().getCaptureProgress(), 1e-9);
		assertEquals(3, h.state().getGuardsCounted());
		assertTrue(h.failedReasons().isEmpty(), "no DEFENDED while the bar never moved");
	}

	@Test
	@DisplayName("DEFENDED still fires once the bar is pushed back down to 0 from above")
	void guardsPushBarToZero_defended() {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, 0, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		h.guards.put(1, 3);
		h.join(2);
		contest(h, 2, 3.0, T0);

		h.tick(T0 + 1_000L);

		assertEquals(TurfState.IDLE, h.state().getState());
		assertEquals(TurfCaptureFailedEvent.Reason.DEFENDED, onlyFailure(h));
	}
}
