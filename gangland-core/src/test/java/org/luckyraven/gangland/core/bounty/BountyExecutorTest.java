package org.luckyraven.gangland.core.bounty;

import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.events.bounty.BountyEvent;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.timer.Timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The periodic bounty growth compounds only the server-made notoriety: what players posted is escrow and stays put
 * (WB-16 cap clamp, and the minted-money doubling of the posted part).
 */
@DisplayName("BountyExecutor - the timer grows notoriety only")
class BountyExecutorTest {

	private BukkitStatics bukkit;
	private Bounty        bounty;
	private Timer         timer;
	private BountyEvent   event;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		bounty = new Bounty(Currency.of(0), 0.0);
		timer  = mock(Timer.class);
		event  = mock(BountyEvent.class);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a tick doubles the notoriety and leaves the posted escrow untouched")
	void growsNotorietyOnly_postedUntouched() {
		post(1000);
		bounty.addNotoriety(Currency.of(100));

		executor(20_000).execute(timer);

		assertEquals(Currency.of(200), bounty.getNotoriety());
		assertEquals(Currency.of(1000), bounty.getPostedAmount());
		assertEquals(Currency.of(1200), bounty.getAmount());
		verify(timer, never()).stop();
	}

	@Test
	@DisplayName("WB-16: the last doubling is clamped to the cap (15,000 becomes 20,000, not 30,000)")
	void capClampsTheLastDoubling() {
		bounty.addNotoriety(Currency.of(15_000));

		executor(20_000).execute(timer);

		assertEquals(Currency.of(20_000), bounty.getNotoriety());
	}

	@Test
	@DisplayName("a bounty that is posted money only stops the timer and grows nothing")
	void postedOnlyBounty_stopsTheTimer() {
		post(1000);

		executor(20_000).execute(timer);

		verify(timer).stop();
		assertEquals(Currency.of(1000), bounty.getAmount());
	}

	@Test
	@DisplayName("WB-11: after one cancelled tick, the next uncancelled tick still grows the notoriety")
	void cancelledTick_thenUncancelledTick_grows() {
		bounty.addNotoriety(Currency.of(100));
		BountyEvent shared = new BountyEvent(false, bounty) {
			@Override
			public HandlerList getHandlers() {
				return new HandlerList();
			}
		};

		boolean[] cancelNext = {true};
		doAnswer(inv -> {
			if (cancelNext[0]) ((BountyEvent) inv.getArgument(0)).setCancelled(true);
			return null;
		}).when(bukkit.pluginManager()).callEvent(any());

		BountyExecutor executor = executor(20_000, shared);
		executor.execute(timer);
		assertEquals(Currency.of(100), bounty.getNotoriety(), "the cancelled tick grows nothing");

		cancelNext[0] = false;
		executor.execute(timer);
		assertEquals(Currency.of(200), bounty.getNotoriety(), "the next tick is not cancelled by the stale flag");
	}

	private void post(int amount) {
		CommandSender poster = mock(CommandSender.class);
		when(poster.getName()).thenReturn("poster");
		bounty.addBounty(poster, Currency.of(amount), 0);
	}

	private BountyExecutor executor(double max) {
		return executor(max, event);
	}

	private BountyExecutor executor(double max, BountyEvent withEvent) {
		BountySettings settings = mock(BountySettings.class);
		when(settings.getTimerMultiple()).thenReturn(2.0);
		when(settings.getTimerMax()).thenReturn(max);
		BountyContext context = mock(BountyContext.class);
		when(context.getBounty()).thenReturn(bounty);
		when(context.getUserLevel()).thenReturn(0);

		return new BountyExecutor(mock(JavaPlugin.class), withEvent, context, settings);
	}

}
