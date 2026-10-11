package org.luckyraven.gangland.fights.fight;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.fights.fight.ChallengeRules.Candidate;
import org.luckyraven.gangland.fights.fight.ChallengeRules.Party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ChallengeRules - the challenge matrix")
class FightRulesTest {

	static FightSettings settings() {
		return new FightSettings(60, 60, 10, 10, 5, 1, 5, true, 60, "MOST_ALIVE", 0, 100000, 50, 1000, 24, 7);
	}

	private static final Party     OFFICER = new Party(true, true, 10000, 30);
	private static final Party     PLAIN   = new Party(false, false, 10000, 30);
	private static final Party     TARGET  = new Party(true, true, 10000, 30);
	private static final Candidate OK      = new Candidate(false, false, false, 100);

	@Test
	@DisplayName("allied_refused")
	void allied_refused() {
		assertEquals("Challenge.Allied", ChallengeRules.propose(settings(), false, true, OFFICER, TARGET, 2, 0));
		assertNull(ChallengeRules.propose(settings(), false, false, OFFICER, TARGET, 2, 0));
	}

	@Test
	@DisplayName("sameGang")
	void sameGang() {
		assertEquals("Challenge.Same_Gang", ChallengeRules.propose(settings(), true, false, OFFICER, TARGET, 2, 0));
	}

	@Test
	@DisplayName("sizeBounds")
	void sizeBounds() {
		assertEquals("Challenge.Size_Range", ChallengeRules.propose(settings(), false, false, OFFICER, TARGET, 0, 0));
		assertEquals("Challenge.Size_Range", ChallengeRules.propose(settings(), false, false, OFFICER, TARGET, 6, 0));
		assertNull(ChallengeRules.propose(settings(), false, false, OFFICER, TARGET, 1, 0));
		assertNull(ChallengeRules.propose(settings(), false, false, OFFICER, TARGET, 5, 0));
	}

	@Test
	@DisplayName("rankGate - a member without the Fight node cannot propose or accept")
	void rankGate() {
		assertEquals("Challenge.Rank_Needed", ChallengeRules.propose(settings(), false, false, PLAIN, TARGET, 2, 0));
		assertEquals("Challenge.Rank_Needed", ChallengeRules.accept(settings(), PLAIN, 0));
	}

	@Test
	@DisplayName("betNeedsWithdrawBothSides")
	void betNeedsWithdrawBothSides() {
		Party fightOnly = new Party(true, false, 10000, 30);
		assertEquals("Challenge.Withdraw_Needed",
		             ChallengeRules.propose(settings(), false, false, fightOnly, TARGET, 2, 500));
		assertEquals("Challenge.Withdraw_Needed", ChallengeRules.accept(settings(), fightOnly, 500));
		assertNull(ChallengeRules.propose(settings(), false, false, fightOnly, TARGET, 2, 0));
		assertNull(ChallengeRules.accept(settings(), fightOnly, 0));
		assertNull(ChallengeRules.accept(settings(), OFFICER, 500));
	}

	@Test
	@DisplayName("betCaps - Max_Bet and a percent of each bank")
	void betCaps() {
		assertEquals("Challenge.Bet_Too_High", ChallengeRules.propose(settings(), false, false,
		                                                              new Party(true, true, 1e9, 30), TARGET, 2,
		                                                              100001));
		assertEquals("Challenge.Bet_Over_Bank",
		             ChallengeRules.propose(settings(), false, false, OFFICER, TARGET, 2, 5001));
		assertEquals("Challenge.Bet_Over_Bank", ChallengeRules.propose(settings(), false, false, OFFICER,
		                                                               new Party(true, true, 100, 30), 2, 500));
		assertEquals("Challenge.Bet_Over_Bank", ChallengeRules.accept(settings(), new Party(true, true, 100, 30), 500));
		assertEquals("Challenge.Bet_Invalid",
		             ChallengeRules.propose(settings(), false, false, OFFICER, TARGET, 2, -1));
	}

	@Test
	@DisplayName("rankedDecision - the bet decides, and a young opponent gang refuses ranked")
	void rankedDecision() {
		assertTrue(settings().ranked(1000));
		assertFalse(settings().ranked(999));
		assertFalse(settings().ranked(0));
		assertEquals("Challenge.Ranked_Gang_Too_Young", ChallengeRules.propose(settings(), false, false, OFFICER,
		                                                                       new Party(true, true, 10000, 2), 2,
		                                                                       1000));
		assertNull(ChallengeRules.propose(settings(), false, false, OFFICER, new Party(true, true, 10000, 2), 2, 500));
	}

	@Test
	@DisplayName("taggedAtEnlist_refused")
	void taggedAtEnlist_refused() {
		assertEquals("Challenge.Tagged",
		             ChallengeRules.enlist(settings(), new Candidate(true, false, false, 100), false));
		assertNull(ChallengeRules.enlist(settings(), OK, false));
	}

	@Test
	@DisplayName("downedOrWanted_refused")
	void downedOrWanted_refused() {
		assertEquals("Challenge.Downed",
		             ChallengeRules.enlist(settings(), new Candidate(false, true, false, 100), false));
		assertEquals("Challenge.Wanted",
		             ChallengeRules.enlist(settings(), new Candidate(false, false, true, 100), false));
	}

	@Test
	@DisplayName("youngMember_rankedRefused")
	void youngMember_rankedRefused() {
		Candidate young = new Candidate(false, false, false, 5);
		assertEquals("Challenge.Member_Too_Young", ChallengeRules.enlist(settings(), young, true));
		assertNull(ChallengeRules.enlist(settings(), young, false));
		assertNull(ChallengeRules.enlist(settings(), new Candidate(false, false, false, 24), true));
	}
}
