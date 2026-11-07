package org.luckyraven.gangland.core.events.bounty;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.bounty.Bounty;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("BountyEvent - cancellation flag")
class BountyEventTest {

	private static final HandlerList HANDLERS = new HandlerList();

	private static BountyEvent event(Bounty bounty) {
		return new BountyEvent(false, bounty, BigDecimal.TEN) {
			@NotNull
			@Override
			public HandlerList getHandlers() {
				return HANDLERS;
			}
		};
	}

	@Test
	@DisplayName("a new event is not cancelled, setCancelled flips the flag both ways, also through Cancellable")
	void isCancelled_followsSetCancelled() {
		BountyEvent event = event(mock(Bounty.class));

		assertFalse(event.isCancelled());

		event.setCancelled(true);
		assertTrue(event.isCancelled());
		assertTrue(((Cancellable) event).isCancelled());

		event.setCancelled(false);
		assertFalse(event.isCancelled());
	}

	@Test
	@DisplayName("the bounty it was built with is the one it reports")
	void getBounty_returnsTheConstructorArgument() {
		Bounty bounty = mock(Bounty.class);

		assertSame(bounty, event(bounty).getBounty());
	}
}
