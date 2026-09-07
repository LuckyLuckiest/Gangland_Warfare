package org.luckyraven.gangland.copsncrooks.seam;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.combo.KillCombo;
import org.luckyraven.gangland.copsncrooks.combo.KillComboTracker;
import org.luckyraven.gangland.copsncrooks.events.combo.KillComboEvent;
import org.luckyraven.gangland.copsncrooks.npc.entity.EntityMarkManager;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the seam-3 delegate: {@code onWantedTrigger}/{@code onComboReset}/{@code onVictimDeath} must forward through
 * to {@link KillCombo}'s three setters (translating the {@link KillComboEvent} down to the {@link Player} the core
 * {@code WantedKillTracker} contract expects), and {@code countsForWanted} must delegate to
 * {@link EntityMarkManager}. See documentation/module-loader.md, "Core seams".
 */
@DisplayName("KillComboWantedTracker")
class KillComboWantedTrackerTest {

	private KillCombo              killCombo;
	private EntityMarkManager      entityMarks;
	private KillComboWantedTracker tracker;

	@BeforeEach
	void setUp() {
		killCombo   = mock(KillCombo.class);
		entityMarks = mock(EntityMarkManager.class);
		tracker     = new KillComboWantedTracker(killCombo, entityMarks);
	}

	@Test
	@DisplayName("countsForWanted delegates to EntityMarkManager")
	void countsForWanted_delegatesToEntityMarkManager() {
		Entity victim = mock(Entity.class);
		when(entityMarks.countsForWanted(victim)).thenReturn(true);

		assertTrue(tracker.countsForWanted(victim));
		verify(entityMarks).countsForWanted(victim);
	}

	@Test
	@DisplayName("onWantedTrigger forwards through KillCombo.setOnWantedLevelTrigger, translating the event to a Player")
	@SuppressWarnings("unchecked")
	void onWantedTrigger_forwardsThroughKillCombo() {
		List<Player> received = new ArrayList<>();
		Player       player   = mock(Player.class);

		tracker.onWantedTrigger(received::add);

		ArgumentCaptor<Consumer<KillComboEvent>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(killCombo).setOnWantedLevelTrigger(captor.capture());
		captor.getValue().accept(new KillComboEvent(player, mock(KillComboTracker.class)));

		assertEquals(List.of(player), received);
	}

	@Test
	@DisplayName("onComboReset forwards through KillCombo.setOnComboReset, translating the event to a Player")
	@SuppressWarnings("unchecked")
	void onComboReset_forwardsThroughKillCombo() {
		List<Player> received = new ArrayList<>();
		Player       player   = mock(Player.class);

		tracker.onComboReset(received::add);

		ArgumentCaptor<Consumer<KillComboEvent>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(killCombo).setOnComboReset(captor.capture());
		captor.getValue().accept(new KillComboEvent(player, mock(KillComboTracker.class)));

		assertEquals(List.of(player), received);
	}

	@Test
	@DisplayName("onVictimDeath forwards through KillCombo.setOnPlayerDeath")
	@SuppressWarnings("unchecked")
	void onVictimDeath_forwardsThroughKillCombo() {
		List<UUID> received = new ArrayList<>();
		UUID       victimId = UUID.randomUUID();

		tracker.onVictimDeath(received::add);

		ArgumentCaptor<Consumer<UUID>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(killCombo).setOnPlayerDeath(captor.capture());
		captor.getValue().accept(victimId);

		assertEquals(List.of(victimId), received);
	}
}
