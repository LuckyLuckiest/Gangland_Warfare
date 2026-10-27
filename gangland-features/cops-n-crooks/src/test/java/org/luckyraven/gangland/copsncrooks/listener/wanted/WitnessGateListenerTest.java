package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolManager;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.WitnessSettings;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSupport;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WitnessGateListener - a gated crime nobody official saw waits for a 911 call (P-WIT-1, 4, 5)")
class WitnessGateListenerTest {

	private final ChaseConfigLoader config    = mock(ChaseConfigLoader.class);
	private final WitnessService    witnesses = mock(WitnessService.class);
	private final CopManager        copManager = mock(CopManager.class);
	private final PatrolManager     patrol    = mock(PatrolManager.class);
	private final CopLoader         copLoader = mock(CopLoader.class);
	private final CopConfigProvider provider  = mock(CopConfigProvider.class);
	private final Player            player    = mock(Player.class);
	private final UUID              playerId  = UUID.randomUUID();
	private final Location          where     = new Location(mock(World.class), 10, 64, 10);

	private WitnessGateListener      listener;
	private MockedStatic<NpcSupport> npcSupport;

	@BeforeEach
	void setUp() {
		when(player.getUniqueId()).thenReturn(playerId);
		when(config.getWitnesses()).thenReturn(WitnessSettings.DEFAULT);
		when(witnesses.witness(any(), any(), any())).thenReturn(1);
		when(copLoader.getLoadedProvider()).thenReturn(provider);
		when(provider.getAlertRange()).thenReturn(40.0);
		listener   = new WitnessGateListener(config, witnesses, copManager, patrol, new CopPerception(), copLoader);
		npcSupport = mockStatic(NpcSupport.class);
		npcSupport.when(NpcSupport::available).thenReturn(true);
	}

	@AfterEach
	void tearDown() {
		npcSupport.close();
	}

	private CrimeCommittedEvent crime(String id, boolean seenByCop, int witnessCount) {
		return new CrimeCommittedEvent(player, id, where, seenByCop, witnessCount);
	}

	@Test
	@DisplayName("Car_Theft, no cop, no witness, no cop sight: cancelled and the witness service asked exactly once")
	void gatedUnseenCrimeIsCancelledAndWitnessed() {
		CrimeCommittedEvent event = crime("Car_Theft", false, 0);

		listener.onCrime(event);

		assertTrue(event.isCancelled());
		verify(witnesses, times(1)).witness(eq(player), eq("Car_Theft"), eq(where));
	}

	@Test
	@DisplayName("0.21.1 Q1: an unseen Kill_Civilian waits for a witness like Car_Theft (cancelled, witness asked once)")
	void unseenKillCivilianIsWitnessGated() {
		CrimeCommittedEvent event = crime("Kill_Civilian", false, 0);

		listener.onCrime(event);

		assertTrue(event.isCancelled());
		verify(witnesses, times(1)).witness(eq(player), eq("Kill_Civilian"), eq(where));
	}

	@Test
	@DisplayName("nobody saw it (witness() returns 0): the crime stays cancelled, so it is lost with no heat")
	void unwitnessedCrimeIsLost() {
		when(witnesses.witness(any(), any(), any())).thenReturn(0);
		CrimeCommittedEvent event = crime("Car_Theft", false, 0);

		listener.onCrime(event);

		assertTrue(event.isCancelled());
	}

	@Test
	@DisplayName("a cop saw it (event flag): not cancelled, no witness lookup")
	void seenByCopPassesThrough() {
		CrimeCommittedEvent event = crime("Car_Theft", true, 0);

		listener.onCrime(event);

		assertFalse(event.isCancelled());
		verify(witnesses, never()).witness(any(), any(), any());
	}

	@Test
	@DisplayName("the player's squad has a fresh sighting: not cancelled, no witness lookup")
	void copSightPassesThrough() {
		NpcSquad squad = new NpcSquad();
		squad.reportSighting(where);
		CopGroup group = mock(CopGroup.class);
		when(group.getSquad()).thenReturn(squad);
		when(copManager.groupOf(playerId)).thenReturn(group);
		CrimeCommittedEvent event = crime("Car_Theft", false, 0);

		listener.onCrime(event);

		assertFalse(event.isCancelled());
		verify(witnesses, never()).witness(any(), any(), any());
	}

	/** A live cop whose sight of the player is {@code sees} within the alert range. */
	private CopNpc cop(boolean sees) {
		CopNpc cop = mock(CopNpc.class);
		when(cop.isValid()).thenReturn(true);
		when(cop.canSee(eq(player), anyDouble())).thenReturn(sees);
		return cop;
	}

	@Test
	@DisplayName("0.21.1: a civilian kill right in front of a squad cop (any group) is not gated: no witness needed")
	void killInSightOfASquadCopIsNotGated() {
		List<CopNpc> cops = List.of(cop(false), cop(true));
		when(copManager.liveCops()).thenReturn(cops);
		CrimeCommittedEvent event = crime("Kill_Civilian", false, 0);

		listener.onCrime(event);

		assertFalse(event.isCancelled());
		verify(witnesses, never()).witness(any(), any(), any());
	}

	@Test
	@DisplayName("0.21.1: a civilian kill in sight of a beat (patrol) cop is not gated")
	void killInSightOfABeatCopIsNotGated() {
		List<CopNpc> beat = List.of(cop(true));
		when(patrol.beatCops()).thenReturn(beat);
		CrimeCommittedEvent event = crime("Kill_Civilian", false, 0);

		listener.onCrime(event);

		assertFalse(event.isCancelled());
		verify(witnesses, never()).witness(any(), any(), any());
	}

	@Test
	@DisplayName("0.21.1: cops nearby that cannot see the player do not excuse the gate")
	void copsThatCannotSeeStillGate() {
		List<CopNpc> squad = List.of(cop(false));
		List<CopNpc> beat  = List.of(cop(false));
		when(copManager.liveCops()).thenReturn(squad);
		when(patrol.beatCops()).thenReturn(beat);
		CrimeCommittedEvent event = crime("Kill_Civilian", false, 0);

		listener.onCrime(event);

		assertTrue(event.isCancelled());
	}

	@Test
	@DisplayName("a group whose squad has not seen the player recently does not excuse the gate")
	void staleSquadStillGates() {
		CopGroup group = mock(CopGroup.class);
		when(group.getSquad()).thenReturn(new NpcSquad());
		when(copManager.groupOf(playerId)).thenReturn(group);
		CrimeCommittedEvent event = crime("Car_Theft", false, 0);

		listener.onCrime(event);

		assertTrue(event.isCancelled());
	}

	@Test
	@DisplayName("the 911 recommit carries one witness: it passes through")
	void witnessedRecommitPassesThrough() {
		CrimeCommittedEvent event = crime("Car_Theft", false, 1);

		listener.onCrime(event);

		assertFalse(event.isCancelled());
		verify(witnesses, never()).witness(any(), any(), any());
	}

	@Test
	@DisplayName("crimes outside Wanted.Witnesses.Crimes are never gated (P-WIT-5; Kill_Civilian joined the list in 0.21.1)")
	void shippedCrimesNeverGated() {
		for (String id : List.of("Kill_Player", "Kill_Cop", "Assault_Cop", "Resisting_Arrest", "Store_Robbery")) {
			CrimeCommittedEvent event = crime(id, false, 0);

			listener.onCrime(event);

			assertFalse(event.isCancelled(), id);
		}
		verify(witnesses, never()).witness(any(), any(), any());
	}

	@Test
	@DisplayName("Assault_Civilian is gated by default")
	void assaultCivilianGated() {
		CrimeCommittedEvent event = crime("Assault_Civilian", false, 0);

		listener.onCrime(event);

		assertTrue(event.isCancelled());
		verify(witnesses).witness(player, "Assault_Civilian", where);
	}

	@Test
	@DisplayName("an id added to Wanted.Witnesses.Crimes is gated too")
	void configuredIdGated() {
		when(config.getWitnesses()).thenReturn(new WitnessSettings(true, List.of("Kill_Player")));
		CrimeCommittedEvent event = crime("Kill_Player", false, 0);

		listener.onCrime(event);

		assertTrue(event.isCancelled());
	}

	@Test
	@DisplayName("Wanted.Witnesses.Enable false: nothing is gated")
	void disabledMeansNoGate() {
		when(config.getWitnesses()).thenReturn(new WitnessSettings(false, List.of("Car_Theft")));
		CrimeCommittedEvent event = crime("Car_Theft", false, 0);

		listener.onCrime(event);

		assertFalse(event.isCancelled());
		verify(witnesses, never()).witness(any(), any(), any());
	}

	@Test
	@DisplayName("an explicitly empty crime list gates nothing")
	void emptyListMeansNoGate() {
		when(config.getWitnesses()).thenReturn(new WitnessSettings(true, List.of()));
		CrimeCommittedEvent event = crime("Car_Theft", false, 0);

		listener.onCrime(event);

		assertFalse(event.isCancelled());
	}

	@Test
	@DisplayName("Citizens absent: no gate, the crime counts at once (shipped behaviour)")
	void noCitizensMeansNoGate() {
		npcSupport.when(NpcSupport::available).thenReturn(false);
		CrimeCommittedEvent event = crime("Car_Theft", false, 0);

		listener.onCrime(event);

		assertFalse(event.isCancelled());
		verify(witnesses, never()).witness(any(), any(), any());
	}
}
