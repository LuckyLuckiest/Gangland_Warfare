package org.luckyraven.gangland.fights.fight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.fight.FightStateMachineTest.FakeArenas;
import org.luckyraven.gangland.fights.fight.FightStateMachineTest.FakeClock;
import org.luckyraven.gangland.fights.fight.FightStateMachineTest.FakeSettlement;
import org.luckyraven.gangland.fights.fight.FightStateMachineTest.FakeTag;
import org.luckyraven.gangland.fights.fight.FightStateMachineTest.FakeWorld;
import org.luckyraven.gangland.fights.stat.FightHistory;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Fight limits - caps, arena cooldown, same address")
class FightLimitsTest {

	static final int V = FightStateMachineTest.VIPERS;
	static final int R = FightStateMachineTest.RATS;

	FightRules   rules;
	FightHistory history;
	FakeWorld    world;
	FakeClock    clock;
	FightManager manager;
	RulesFightLimits limits;
	UUID v1, v2, r1, r2;
	String refusal;
	long   cooldownMillis = 30_000;

	@BeforeEach
	void setUp() {
		rules = mock(FightRules.class);
		when(rules.getMaxBet()).thenReturn(100_000D);
		when(rules.getMaxBetPercentOfBank()).thenReturn(50);
		when(rules.getMaxNetTransferPerPairPerDay()).thenReturn(200_000D);
		when(rules.getMaxBetTransferPerGangPerDay()).thenReturn(300_000D);
		when(rules.getRankedMinOpponentGangAgeDays()).thenReturn(7);
		when(rules.getRankedPerPairPerDay()).thenReturn(3);
		when(rules.getRematchCooldownMinutes()).thenReturn(30);
		when(rules.getArenaCooldownSeconds()).thenReturn(30);
		history = mock(FightHistory.class);
		when(history.millisSinceLastRanked(anyInt(), anyInt(), anyLong())).thenReturn(Long.MAX_VALUE);

		clock = new FakeClock();
		world = new FakeWorld();
		v1 = UUID.randomUUID();
		v2 = UUID.randomUUID();
		r1 = UUID.randomUUID();
		r2 = UUID.randomUUID();
		world.gang.put(v1, V);
		world.gang.put(v2, V);
		world.gang.put(r1, R);
		world.gang.put(r2, R);
		limits  = new RulesFightLimits(rules, new FightBetCaps(rules), history, world, () -> clock.now);
		manager = new FightManager(clock, new FakeSettlement(), new FakeTag(), new FakeArenas(), world,
		                           FightRulesTest::settings, limits);
	}

	@Test
	@DisplayName("pairCap: refused at challenge and at accept")
	void pairCap() {
		when(history.pairNetToday(anyInt(), anyInt(), anyLong())).thenReturn(199_500D);
		manager.propose(v1, "Rats", 2, 1000);
		assertTrue(world.told(v1, "Economy.Pair_Cap"));
		assertTrue(manager.active().isEmpty());

		// proposal made while the pair was fine, the cap fills before accept
		when(history.pairNetToday(anyInt(), anyInt(), anyLong())).thenReturn(0D);
		manager.propose(v1, "Rats", 2, 1000);
		when(history.pairNetToday(anyInt(), anyInt(), anyLong())).thenReturn(199_500D);
		manager.accept(r1, "Vipers");
		assertTrue(world.told(r1, "Economy.Pair_Cap"));
		assertEquals(FightState.PROPOSED, manager.active().iterator().next().state());
	}

	@Test
	@DisplayName("gangCap: a gang over its daily winnings is refused")
	void gangCap() {
		when(history.wonToday(anyInt(), anyLong())).thenReturn(299_500D);
		manager.propose(v1, "Rats", 2, 1000);
		assertTrue(world.told(v1, "Economy.Gang_Cap"));
	}

	@Test
	@DisplayName("rankedAndRematch: daily ranked count and rematch cooldown refuse a ranked fight, not a sparring bet")
	void rankedAndRematch() {
		when(history.rankedBetweenToday(anyInt(), anyInt(), anyLong())).thenReturn(3);
		manager.propose(v1, "Rats", 2, 1000);
		assertTrue(world.told(v1, "Economy.Ranked_Pair_Limit"));

		manager.propose(v1, "Rats", 2, 500); // below Ranked_Min_Bet: sparring
		assertEquals(1, manager.active().size());

		manager = new FightManager(clock, new FakeSettlement(), new FakeTag(), new FakeArenas(), world,
		                           FightRulesTest::settings, limits);
		when(history.rankedBetweenToday(anyInt(), anyInt(), anyLong())).thenReturn(0);
		when(history.millisSinceLastRanked(anyInt(), anyInt(), anyLong())).thenReturn(60_000L);
		manager.propose(v1, "Rats", 2, 1000);
		assertTrue(world.told(v1, "Economy.Ranked_Rematch_Cooldown"));
	}

	@Test
	@DisplayName("sameAddress: refused only when enabled and the two sides share an IP")
	void sameAddress() {
		world.address.put(v1, "1.2.3.4");
		world.address.put(r2, "1.2.3.4");
		manager.propose(v1, "Rats", 2, 1000);
		assertEquals(1, manager.active().size()); // off by default

		manager.abortAll("t");
		when(rules.isBlockSameAddress()).thenReturn(true);
		manager.propose(v1, "Rats", 2, 1000);
		assertTrue(world.told(v1, "Economy.Same_Address"));
		assertTrue(manager.active().isEmpty());

		world.address.put(r2, "5.6.7.8");
		manager.propose(v1, "Rats", 2, 1000);
		assertEquals(1, manager.active().size());
	}

	@Test
	@DisplayName("sameAddress: a no-bet fight is never refused, even when enabled and the sides share an IP")
	void sameAddressNoBetNotChecked() {
		when(rules.isBlockSameAddress()).thenReturn(true);
		world.address.put(v1, "1.2.3.4");
		world.address.put(r2, "1.2.3.4");
		manager.propose(v1, "Rats", 2, 0);
		assertEquals(1, manager.active().size());
		assertFalse(world.told(v1, "Economy.Same_Address"));
	}

	@Test
	@DisplayName("arenaCooldown: the arena stays closed for Arena_Cooldown_Seconds after the fight ends")
	void arenaCooldown() {
		manager.propose(v1, "Rats", 2, 0);
		manager.accept(r1, "Vipers");
		Fight f = manager.active().iterator().next();
		manager.enlist(v1);
		manager.enlist(v2);
		manager.enlist(r1);
		manager.enlist(r2);
		clock.advance(10);
		manager.forfeit(r1, "x");
		manager.forfeit(r2, "x");
		clock.advance(5);
		assertEquals(FightState.ENDED, f.state());

		manager.propose(v1, "Rats", 2, 0);
		manager.accept(r1, "Vipers");
		assertTrue(world.told(r1, "Challenge.No_Arena"));

		clock.advance(31);
		manager.accept(r1, "Vipers");
		assertEquals(FightState.LOBBY, manager.active().iterator().next().state());
	}
}
