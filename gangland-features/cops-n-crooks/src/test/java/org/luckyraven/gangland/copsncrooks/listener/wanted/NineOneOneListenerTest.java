package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.events.CivilianCalledInEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.data.region.PlaceNames;
import org.luckyraven.keystone.npc.NpcSquad;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("NineOneOneListener - a landed 911 call counts the crime, sights the squad and plays Caller_911 (P-WIT-2)")
class NineOneOneListenerTest {

	private final CrimeService crimes     = mock(CrimeService.class);
	private final CopManager   copManager = mock(CopManager.class);
	private final CopRadio     radio      = mock(CopRadio.class);
	private final PlaceNames   places     = mock(PlaceNames.class);
	private final Player       player     = mock(Player.class);
	private final UUID         playerId   = UUID.randomUUID();
	private final Location     at         = new Location(mock(World.class), 100, 64, -20);
	private final NpcSquad     squad      = new NpcSquad();
	private final CopGroup     group      = mock(CopGroup.class);

	private NineOneOneListener listener;

	@BeforeEach
	void setUp() {
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getName()).thenReturn("Bob");
		when(player.isOnline()).thenReturn(true);
		when(group.getSquad()).thenReturn(squad);
		when(copManager.groupOf(playerId)).thenReturn(group);
		when(crimes.commit(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
		                   org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
		when(places.locate(any())).thenReturn(Optional.of("Docks"));
		listener = new NineOneOneListener(crimes, copManager, radio, places);
	}

	private CivilianCalledInEvent call(String crimeId) {
		return new CivilianCalledInEvent(player, crimeId, at, mock(LivingEntity.class));
	}

	@Test
	@DisplayName("commit(player, crime, at, false, 1), then squad sighted at the crime spot, then the Caller_911 line")
	void landedCallCommitsWithOneWitnessAndSights() {
		listener.onCalledIn(call("Car_Theft"));

		InOrder order = inOrder(crimes, copManager, radio);
		order.verify(crimes).commit(player, "Car_Theft", at, false, 1);
		order.verify(copManager).groupOf(playerId);
		assertTrue(squad.hasFreshSighting());

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> placeholders = ArgumentCaptor.forClass(Map.class);
		order.verify(radio).sayFromLeader(eq(group), eq("Caller_911"), placeholders.capture());
		assertEquals("Car_Theft", placeholders.getValue().get("crime"));
		assertEquals("Docks", placeholders.getValue().get("place"));
		// %suspect% belongs to the CopRadio extras hook (disguise lane): a line-level value would leak the real name
		assertEquals(java.util.Set.of("crime", "place"), placeholders.getValue().keySet());
	}

	@Test
	@DisplayName("no named place at the spot: the place placeholder still has a value")
	void unnamedPlaceHasFallback() {
		when(places.locate(any())).thenReturn(Optional.empty());

		listener.onCalledIn(call("Car_Theft"));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> placeholders = ArgumentCaptor.forClass(Map.class);
		verify(radio).sayFromLeader(eq(group), eq("Caller_911"), placeholders.capture());
		assertFalse(placeholders.getValue().get("place").isBlank());
	}

	@Test
	@DisplayName("the player has no cop group (no stars even after the commit): the crime is counted, no radio, no error")
	void noGroupOnlyCommits() {
		when(copManager.groupOf(playerId)).thenReturn(null);

		listener.onCalledIn(call("Car_Theft"));

		verify(crimes).commit(player, "Car_Theft", at, false, 1);
		verify(radio, never()).sayFromLeader(any(), anyString(), anyMap());
		assertFalse(squad.hasFreshSighting());
	}

	@Test
	@DisplayName("the commit was cancelled by someone else: the squad is not sighted and the radio stays quiet")
	void cancelledCommitDoesNotAlertCops() {
		when(crimes.commit(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
		                   org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);

		listener.onCalledIn(call("Car_Theft"));

		assertFalse(squad.hasFreshSighting());
		verify(radio, never()).sayFromLeader(any(), anyString(), anyMap());
	}

	@Test
	@DisplayName("the criminal went offline before the call landed: nothing is committed")
	void offlineCriminalIgnored() {
		when(player.isOnline()).thenReturn(false);

		listener.onCalledIn(call("Car_Theft"));

		verify(crimes, never()).commit(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
		                               org.mockito.ArgumentMatchers.anyInt());
	}

	@Test
	@DisplayName("judge-fix r1: no named place uses the localized CopRadio.placeOf word, not hard-coded English")
	void unnamedPlaceUsesRadioUnknownPlace() {
		when(places.locate(any())).thenReturn(Optional.empty());
		when(radio.placeOf(any())).thenReturn("la zona");

		listener.onCalledIn(call("Car_Theft"));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> placeholders = ArgumentCaptor.forClass(Map.class);
		verify(radio).sayFromLeader(eq(group), eq("Caller_911"), placeholders.capture());
		assertEquals("la zona", placeholders.getValue().get("place"));
	}
}
