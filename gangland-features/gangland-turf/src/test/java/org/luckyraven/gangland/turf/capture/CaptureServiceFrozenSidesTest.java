package org.luckyraven.gangland.turf.capture;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.turf.state.TurfState;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Lane W2-R2, decision A4: while a contest runs, a player counts on any side only if he was in his gang before the
 * contest started ({@code Member.gangJoinDateLong} before {@code contestStartedAt}), and an ally counts for the owners
 * only if the alliance was formed before it. Later joiners and later allies are bystanders until the contest ends.
 * An unknown member (no record) counts. Every contest here starts at {@link #T} with two attackers of gang 2
 * against owner gang 1 on an owned turf.
 */
@DisplayName("CaptureService - frozen sides during a contest (W2-R2, A4)")
class CaptureServiceFrozenSidesTest {

	private static final long T = W2R2Harness.T0;

	/** Owner gang 1 with a turf, two attackers of gang 2 (no member records), and the contest started at T. */
	private static W2R2Harness startedContest(int maxContestMinutes) {
		W2R2Harness h = new W2R2Harness(20, W2R2Harness.rules(0, 0, maxContestMinutes, 4));
		h.gang(1);
		h.gang(2);
		h.ownedBy(1);
		h.join(2);
		h.join(2);
		h.tick(T);
		assertEquals(TurfState.CONTESTING, h.state().getState(), "precondition: the contest started at T");
		return h;
	}

	@Test
	@DisplayName("A4: a gang-2 member who joined after the contest started is a bystander, not a third attacker")
	void lateJoinerAttacker_isBystander() {
		W2R2Harness h      = startedContest(0);
		Gang        rivals = h.gangs.findById(2);
		Player      late   = h.join(2);
		h.addMember(rivals, late, T + 1_000L);

		h.tick(T + 2_000L);

		assertEquals(2, h.state().getChallengersInside().get(2), "the late joiner does not count");
	}

	@Test
	@DisplayName("A4: an owner member who joined after the contest started is a bystander, not a defender")
	void lateJoinerOwner_isBystander() {
		W2R2Harness h     = startedContest(0);
		Gang        owner = h.gangs.findById(1);
		Player      late  = h.join(1);
		h.addMember(owner, late, T + 1_000L);

		h.tick(T + 2_000L);

		assertEquals(0, h.state().getDefendersInside());
	}

	@Test
	@DisplayName("A4: an ally whose alliance was formed after the contest started is neither defender nor attacker")
	void lateAlly_isBystander() {
		W2R2Harness h      = startedContest(0);
		Gang        owner  = h.gangs.findById(1);
		Gang        lateAl = h.gang(6);
		when(lateAl.isAlly(owner)).thenReturn(true);
		when(owner.getAllies()).thenReturn(Set.of(new GangAlliance(owner, lateAl, T + 1_000L)));
		h.join(6);

		h.tick(T + 2_000L);

		assertEquals(0, h.state().getDefendersInside(), "not counted for the owners");
		assertFalse(h.state().getChallengersInside().containsKey(6), "not counted as an attacker either");
	}

	@Test
	@DisplayName("A4: the bystanders count again once the contest has ended (stalled), at the next IDLE tick")
	void afterContest_theyCountAgain() {
		W2R2Harness h      = startedContest(1);
		Gang        rivals = h.gangs.findById(2);
		Player      late   = h.join(2);
		h.addMember(rivals, late, T + 1_000L);

		h.tick(T + 2_000L);
		assertEquals(2, h.state().getChallengersInside().get(2), "during the contest the late joiner is out");

		h.tick(T + 60_000L);
		assertEquals(TurfState.IDLE, h.state().getState(), "the contest stalled at Max_Contest_Minutes");

		h.tick(T + 61_000L);
		assertEquals(3, h.state().getChallengersInside().get(2), "after the contest he counts");
	}

	@Test
	@DisplayName("A4: a player with no member record (legacy row) counts")
	void unknownMember_counts() {
		W2R2Harness h = startedContest(0);
		h.join(2);

		h.tick(T + 2_000L);

		assertEquals(3, h.state().getChallengersInside().get(2), "no record, so the frozen check lets him in");
	}
}
