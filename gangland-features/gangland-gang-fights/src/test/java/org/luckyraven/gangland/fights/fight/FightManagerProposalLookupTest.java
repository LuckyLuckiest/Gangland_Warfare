package org.luckyraven.gangland.fights.fight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luckyraven.gangland.fights.fight.FightStateMachineTest.FakeArenas;
import org.luckyraven.gangland.fights.fight.FightStateMachineTest.FakeClock;
import org.luckyraven.gangland.fights.fight.FightStateMachineTest.FakeSettlement;
import org.luckyraven.gangland.fights.fight.FightStateMachineTest.FakeTag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how {@code accept} and {@code decline} find the PROPOSED fight aimed at the actor's gang: the state and
 * target-gang filters, the optional (null / blank / case-insensitive) proposer-name filter, and that the proposer's
 * name is only looked up for fights that already passed the state and target-gang filters.
 *
 * <p>Four gangs, two disjoint challenges (Vipers to Rats, then Bulls to Hawks), so the Rats and Hawks actors each have
 * an earlier or later fight in the map that must be skipped.
 */
class FightManagerProposalLookupTest {

	private static final int VIPERS = 1;
	private static final int RATS   = 2;
	private static final int BULLS  = 3;
	private static final int HAWKS  = 4;

	private final Map<UUID, Integer>   gangOf = new HashMap<>();
	private final Map<Integer, String> names  = Map.of(VIPERS, "Vipers", RATS, "Rats", BULLS, "Bulls", HAWKS, "Hawks");
	private final List<String>         told   = new ArrayList<>();

	private FightWorld   world;
	private FightManager manager;
	private UUID         vipers, rats, bulls, hawks, stranger;
	private Fight        vipersVsRats, bullsVsHawks;

	@BeforeEach
	void setUp() {
		vipers   = UUID.randomUUID();
		rats     = UUID.randomUUID();
		bulls    = UUID.randomUUID();
		hawks    = UUID.randomUUID();
		stranger = UUID.randomUUID();
		gangOf.put(vipers, VIPERS);
		gangOf.put(rats, RATS);
		gangOf.put(bulls, BULLS);
		gangOf.put(hawks, HAWKS);

		world = mock(FightWorld.class);
		when(world.gangOf(any(UUID.class))).thenAnswer(call -> Optional.ofNullable(gangOf.get(call.<UUID>getArgument(0))));
		when(world.gangByName(anyString())).thenAnswer(call -> names.entrySet().stream().filter(
				entry -> entry.getValue().equalsIgnoreCase(call.<String>getArgument(0))).map(Map.Entry::getKey)
		                                                           .findFirst());
		when(world.gangName(anyInt())).thenAnswer(call -> names.get(call.<Integer>getArgument(0)));
		when(world.mayFight(any(UUID.class))).thenReturn(true);
		when(world.mayWithdraw(any(UUID.class))).thenReturn(true);
		when(world.bank(anyInt())).thenReturn(100000D);
		when(world.gangAgeDays(anyInt())).thenReturn(30L);
		doAnswer(call -> told.add(call.<UUID>getArgument(0) + ":" + call.<String>getArgument(1))).when(world)
				.tell(any(UUID.class), anyString(), any(Object[].class));

		manager = new FightManager(new FakeClock(), new FakeSettlement(), new FakeTag(), new FakeArenas(), world,
		                           FightRulesTest::settings);

		manager.propose(vipers, "Rats", 2, 0);
		manager.propose(bulls, "Hawks", 2, 0);
		List<Fight> fights = new ArrayList<>(manager.active());
		assertEquals(2, fights.size());
		vipersVsRats = fights.get(0);
		bullsVsHawks = fights.get(1);
		assertEquals(VIPERS, vipersVsRats.gangA());
		assertEquals(BULLS, bullsVsHawks.gangA());

		told.clear();
		clearInvocations(world);
	}

	private boolean told(UUID player, String key) {
		return told.contains(player + ":" + key);
	}

	// ------------------------------------------------------------------ accept

	@Test
	@DisplayName("accept without a name takes the proposal aimed at the actor's gang and skips the other fight")
	void accept_noName_takesTheProposalAimedAtTheActorsGang() {
		manager.accept(hawks, null);

		assertEquals(FightState.LOBBY, bullsVsHawks.state());
		assertEquals(FightState.PROPOSED, vipersVsRats.state());
	}

	@Test
	@DisplayName("accept matches the proposer's name case-insensitively")
	void accept_nameIsCaseInsensitive() {
		manager.accept(rats, "vIpErS");

		assertEquals(FightState.LOBBY, vipersVsRats.state());
		assertEquals(FightState.PROPOSED, bullsVsHawks.state());
	}

	@ParameterizedTest(name = "accept with the filter \"{0}\" behaves like no filter")
	@DisplayName("a blank proposer name is no filter")
	@ValueSource(strings = {"", " ", "   "})
	void accept_blankName_isNoFilter(String blank) {
		manager.accept(rats, blank);

		assertEquals(FightState.LOBBY, vipersVsRats.state());
		assertEquals(FightState.PROPOSED, bullsVsHawks.state());
	}

	@Test
	@DisplayName("accept naming a gang that did not challenge finds no proposal; the name is looked up once, only for the fight aimed at the actor")
	void accept_otherGangsName_findsNoProposal() {
		manager.accept(rats, "Bulls");

		assertTrue(told(rats, "Challenge.No_Proposal"), told.toString());
		assertEquals(FightState.PROPOSED, vipersVsRats.state());
		assertEquals(FightState.PROPOSED, bullsVsHawks.state());
		verify(world, times(1)).gangName(VIPERS);
		verify(world, never()).gangName(BULLS);
	}

	@Test
	@DisplayName("a proposal that was already accepted is no longer found")
	void accept_afterAccepting_findsNoProposal() {
		manager.accept(rats, null);
		assertEquals(FightState.LOBBY, vipersVsRats.state());
		told.clear();

		manager.accept(rats, null);

		assertTrue(told(rats, "Challenge.No_Proposal"), told.toString());
	}

	@Test
	@DisplayName("accept by a player in no gang is told so before any lookup")
	void accept_noGang_isToldSo() {
		manager.accept(stranger, "Vipers");

		assertTrue(told(stranger, "Challenge.No_Gang"), told.toString());
		verify(world, never()).gangName(anyInt());
	}

	// ----------------------------------------------------------------- decline

	@Test
	@DisplayName("decline without a name cancels the proposal aimed at the actor's gang, leaves the other fight, and starts the decline cooldown")
	void decline_noName_cancelsTheProposalAimedAtTheActorsGang() {
		manager.decline(hawks);

		assertEquals(FightState.CANCELLED, bullsVsHawks.state());
		assertEquals(FightState.PROPOSED, vipersVsRats.state());

		manager.propose(bulls, "Hawks", 2, 0);
		assertTrue(told(bulls, "Challenge.Cooldown"), told.toString());
	}

	@Test
	@DisplayName("decline matches the proposer's name case-insensitively and looks the name up once")
	void decline_nameIsCaseInsensitive() {
		manager.decline(rats, "vIpErS");

		assertEquals(FightState.CANCELLED, vipersVsRats.state());
		assertEquals(FightState.PROPOSED, bullsVsHawks.state());
		verify(world, times(1)).gangName(VIPERS);
		verify(world, never()).gangName(BULLS);
	}

	@ParameterizedTest(name = "decline with the filter \"{0}\" behaves like no filter")
	@DisplayName("a blank proposer name is no filter")
	@ValueSource(strings = {"", " ", "   "})
	void decline_blankName_isNoFilter(String blank) {
		manager.decline(rats, blank);

		assertEquals(FightState.CANCELLED, vipersVsRats.state());
		verify(world, never()).gangName(VIPERS);
	}

	@Test
	@DisplayName("decline naming a gang that did not challenge finds no proposal; the name is looked up once, only for the fight aimed at the actor")
	void decline_otherGangsName_findsNoProposal() {
		manager.decline(rats, "Bulls");

		assertTrue(told(rats, "Challenge.No_Proposal"), told.toString());
		assertEquals(FightState.PROPOSED, vipersVsRats.state());
		assertEquals(FightState.PROPOSED, bullsVsHawks.state());
		verify(world, times(1)).gangName(VIPERS);
		verify(world, never()).gangName(BULLS);
	}

	@Test
	@DisplayName("a proposal that was already accepted can no longer be declined")
	void decline_afterAccepting_findsNoProposal() {
		manager.accept(rats, null);
		assertEquals(FightState.LOBBY, vipersVsRats.state());
		told.clear();

		manager.decline(rats);

		assertTrue(told(rats, "Challenge.No_Proposal"), told.toString());
		assertEquals(FightState.LOBBY, vipersVsRats.state());
	}

	@Test
	@DisplayName("decline by a player in no gang is told there is no proposal (not No_Gang), without any name lookup")
	void decline_noGang_findsNoProposal() {
		manager.decline(stranger, "Vipers");

		assertTrue(told(stranger, "Challenge.No_Proposal"), told.toString());
		assertFalse(told(stranger, "Challenge.No_Gang"));
		verify(world, never()).gangName(anyInt());
	}
}
