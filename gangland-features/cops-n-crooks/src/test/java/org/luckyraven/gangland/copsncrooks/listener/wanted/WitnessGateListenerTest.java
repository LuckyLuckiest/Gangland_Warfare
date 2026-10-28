package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.WitnessSettings;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.report.CopCrimeReports;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.keystone.npc.NpcSupport;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WitnessGateListener - a gated crime waits for a report: a cop's, else a 911 call (P-WIT-1, 4, 5; 0.21.2)")
class WitnessGateListenerTest {

	private final ChaseConfigLoader config    = mock(ChaseConfigLoader.class);
	private final CopCrimeReports   witnesses = mock(CopCrimeReports.class);
	private final HeatLedger        ledger    = mock(HeatLedger.class);
	private final Player            player    = mock(Player.class);
	private final UUID              playerId  = UUID.randomUUID();
	private final Location          where     = new Location(mock(World.class), 10, 64, 10);

	private WitnessGateListener      listener;
	private MockedStatic<NpcSupport> npcSupport;

	@BeforeEach
	void setUp() {
		when(player.getUniqueId()).thenReturn(playerId);
		when(config.getWitnesses()).thenReturn(WitnessSettings.DEFAULT);
		listener   = new WitnessGateListener(config, witnesses, ledger);
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
		verify(witnesses, times(1)).open(eq(player), eq("Car_Theft"), eq(where), eq(null));
	}

	@Test
	@DisplayName("0.21.1 Q1: an unseen Kill_Civilian waits for a witness like Car_Theft (cancelled, witness asked once)")
	void unseenKillCivilianIsWitnessGated() {
		CrimeCommittedEvent event = crime("Kill_Civilian", false, 0);

		listener.onCrime(event);

		assertTrue(event.isCancelled());
		verify(witnesses, times(1)).open(eq(player), eq("Kill_Civilian"), eq(where), eq(null));
	}

	@Test
	@DisplayName("0.21.2 S4: the hit cop of an Assault_Cop / Kill_Cop goes to the report, so a kill supersedes his assault")
	void copCrime_passesTheVictimToTheReport() {
		UUID cop = UUID.randomUUID();
		when(ledger.currentVictim()).thenReturn(cop);
		CrimeCommittedEvent event = crime("Kill_Cop", false, 0);

		listener.onCrime(event);

		assertTrue(event.isCancelled());
		verify(witnesses).open(player, "Kill_Cop", where, cop);
	}

	@Test
	@DisplayName("a cop saw it (event flag): not cancelled, no witness lookup")
	void seenByCopPassesThrough() {
		CrimeCommittedEvent event = crime("Car_Theft", true, 0);

		listener.onCrime(event);

		assertFalse(event.isCancelled());
		verify(witnesses, never()).open(any(), any(), any(), any());
	}

	@Test
	@DisplayName("flipped 0.21.2 S4 (T-541): an unreported gated Kill_Civilian is held and handed to the report (the "
	             + "listener no longer reads the squad sighting or cops in view)")
	void gatedKillCivilian_heldAndHandedToTheReport() {
		CrimeCommittedEvent event = crime("Kill_Civilian", false, 0);

		listener.onCrime(event);

		assertTrue(event.isCancelled());
		verify(witnesses).open(player, "Kill_Civilian", where, null);
	}

	@Test
	@DisplayName("the 911 recommit carries one witness: it passes through")
	void witnessedRecommitPassesThrough() {
		CrimeCommittedEvent event = crime("Car_Theft", false, 1);

		listener.onCrime(event);

		assertFalse(event.isCancelled());
		verify(witnesses, never()).open(any(), any(), any(), any());
	}

	@Test
	@DisplayName("crimes outside Wanted.Witnesses.Crimes are never gated (P-WIT-5; Kill_Civilian joined in 0.21.1, "
	             + "Kill_Cop and Assault_Cop in 0.21.2)")
	void shippedCrimesNeverGated() {
		for (String id : List.of("Kill_Player", "Resisting_Arrest", "Store_Robbery")) {
			CrimeCommittedEvent event = crime(id, false, 0);

			listener.onCrime(event);

			assertFalse(event.isCancelled(), id);
		}
		verify(witnesses, never()).open(any(), any(), any(), any());
	}

	@Test
	@DisplayName("0.21.2 S4 (T-541): Kill_Cop and Assault_Cop are gated by default; a cop's landed report passes")
	void copCrimesGatedByDefault() {
		for (String id : List.of("Kill_Cop", "Assault_Cop")) {
			CrimeCommittedEvent held = crime(id, false, 0);
			listener.onCrime(held);
			assertTrue(held.isCancelled(), id);

			CrimeCommittedEvent landed = crime(id, true, 2);
			listener.onCrime(landed);
			assertFalse(landed.isCancelled(), id);
		}
	}

	@Test
	@DisplayName("Assault_Civilian is gated by default")
	void assaultCivilianGated() {
		CrimeCommittedEvent event = crime("Assault_Civilian", false, 0);

		listener.onCrime(event);

		assertTrue(event.isCancelled());
		verify(witnesses).open(player, "Assault_Civilian", where, null);
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
		verify(witnesses, never()).open(any(), any(), any(), any());
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
		verify(witnesses, never()).open(any(), any(), any(), any());
	}
}
