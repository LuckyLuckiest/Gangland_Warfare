package org.luckyraven.gangland.gang.wanted;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pins the seam-3 holder's inert-by-default contract: {@code EntityDamageListener} always constructs against
 * {@link WantedKillTrackers}, whether or not the cops-n-crooks module is installed. See
 * documentation/module-loader.md, "Core seams".
 */
@DisplayName("WantedKillTrackers")
class WantedKillTrackersTest {

	@Test
	@DisplayName("with nothing installed the holder is inert: not active, no wanted credit, no throw")
	void noDelegateInstalled_isInert() {
		WantedKillTrackers trackers = new WantedKillTrackers();

		assertFalse(trackers.isActive());
		assertFalse(trackers.countsForWanted(mock(Entity.class)));
		assertDoesNotThrow(() -> trackers.recordKill(mock(Player.class), mock(Wanted.class), mock(Entity.class), 30));
		assertDoesNotThrow(() -> trackers.resetCombo(UUID.randomUUID()));
	}

	@Test
	@DisplayName("a handler registered before install is replayed onto the delegate")
	void handlerRegisteredBeforeInstall_isReplayedOntoDelegate() {
		WantedKillTrackers trackers = new WantedKillTrackers();
		WantedKillTracker  delegate = mock(WantedKillTracker.class);

		@SuppressWarnings("unchecked")
		Consumer<Player> wantedTrigger = mock(Consumer.class);
		@SuppressWarnings("unchecked")
		Consumer<Player> comboReset = mock(Consumer.class);
		@SuppressWarnings("unchecked")
		Consumer<UUID> victimDeath = mock(Consumer.class);

		trackers.onWantedTrigger(wantedTrigger);
		trackers.onComboReset(comboReset);
		trackers.onVictimDeath(victimDeath);

		trackers.install(delegate);

		verify(delegate).onWantedTrigger(wantedTrigger);
		verify(delegate).onComboReset(comboReset);
		verify(delegate).onVictimDeath(victimDeath);
	}

	@Test
	@DisplayName("after install every call forwards to the delegate")
	void afterInstall_everyCallForwards() {
		WantedKillTrackers trackers = new WantedKillTrackers();
		WantedKillTracker  delegate = mock(WantedKillTracker.class);
		trackers.install(delegate);

		Entity victim = mock(Entity.class);
		Player killer = mock(Player.class);
		Wanted wanted = mock(Wanted.class);
		UUID   victimId = UUID.randomUUID();

		trackers.countsForWanted(victim);
		trackers.recordKill(killer, wanted, victim, 30);
		trackers.resetCombo(victimId);

		verify(delegate).countsForWanted(victim);
		verify(delegate).recordKill(killer, wanted, victim, 30);
		verify(delegate).resetCombo(victimId);
	}
}
