package org.luckyraven.gangland.crime;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves {@link CrimeService} publishes the crime through Bukkit's plugin manager and reports whether a listener
 * cancelled it (CONTRACTS C6).
 */
@DisplayName("CrimeService - the crime event bus")
class CrimeServiceTest {

	private final Player   player   = mock(Player.class);
	private final Location location = mock(Location.class);

	@Test
	@DisplayName("commit fires one CrimeCommittedEvent carrying every field, and returns true when nobody cancels")
	void commit_firesTheEventWithEveryField() {
		Location copy = mock(Location.class);
		when(location.clone()).thenReturn(copy);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			boolean result = new CrimeService().commit(player, Crimes.KILL_COP, location, true, 2);

			ArgumentCaptor<CrimeCommittedEvent> captor = ArgumentCaptor.forClass(CrimeCommittedEvent.class);
			verify(bukkit.pluginManager()).callEvent(captor.capture());
			CrimeCommittedEvent event = captor.getValue();

			assertTrue(result);
			assertSame(player, event.getPlayer());
			assertEquals("Kill_Cop", event.getCrimeId());
			assertSame(copy, event.getLocation());
			assertNotSame(location, event.getLocation(), "the event stores a clone, not the caller's mutable Location");
			assertTrue(event.isSeenByCop());
			assertEquals(2, event.getWitnesses());
		}
	}

	@Test
	@DisplayName("a listener that cancels the event makes commit return false")
	void commit_cancelledByAListener_returnsFalse() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			doAnswer(invocation -> {
				((CrimeCommittedEvent) invocation.getArgument(0)).setCancelled(true);
				return null;
			}).when(bukkit.pluginManager()).callEvent(any());

			assertFalse(new CrimeService().commit(player, Crimes.KILL_PLAYER, location));
		}
	}

	@Test
	@DisplayName("the short overload reports an unseen crime with no witnesses")
	void shortCommit_defaultsToUnseenAndNoWitnesses() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			new CrimeService().commit(player, Crimes.ASSAULT_COP, location);

			ArgumentCaptor<CrimeCommittedEvent> captor = ArgumentCaptor.forClass(CrimeCommittedEvent.class);
			verify(bukkit.pluginManager()).callEvent(captor.capture());

			assertFalse(captor.getValue().isSeenByCop());
			assertEquals(0, captor.getValue().getWitnesses());
		}
	}
}
