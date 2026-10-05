package org.luckyraven.gangland.core.wanted;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.events.wanted.WantedStartEvent;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.timer.Timer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pins {@link WantedStars}, the one service that raises, drops and restores stars (CONTRACTS C3): the cause reaches
 * every wanted event, the safety-net decay clock is a SYNC timer, the star-drop price is withdrawn only after the
 * level change succeeded, and the off-thread entry points hop to the main thread.
 *
 * <p>The scheduler from {@link BukkitStatics} runs {@code runTask} inline; {@code runTaskTimer} is verified, never run.
 * Each test sets the starting level before the owner, so no thread stub is consumed by the fixture.
 */
@DisplayName("WantedStars - raising, dropping and restoring stars")
class WantedStarsTest {

	private BukkitStatics  bukkit;
	private JavaPlugin     plugin;
	private WantedSettings settings;
	private Player         owner;
	private Wanted         wanted;
	private FakeContext    context;
	private WantedStars    stars;
	private BukkitTask     clockTask;
	private List<Event>    events;
	private boolean        cancelChanges;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		events = new ArrayList<>();
		doAnswer(invocation -> {
			Event event = invocation.getArgument(0);
			if (cancelChanges && event instanceof WantedLevelChangeEvent change) change.setCancelled(true);
			events.add(event);
			return null;
		}).when(bukkit.pluginManager()).callEvent(any());

		clockTask = mock(BukkitTask.class);
		when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(clockTask);

		plugin   = mock(JavaPlugin.class);
		settings = mock(WantedSettings.class);
		when(settings.isTimerEnabled()).thenReturn(true);
		when(settings.getTimerTime()).thenReturn(120);
		when(settings.getTakeMoneyAmount()).thenReturn(BigDecimal.ZERO);
		when(settings.getWantedDecreasedMessageTemplate()).thenReturn("decreased %level% %stars%");
		when(settings.formatMoneyLoss(any())).thenAnswer(
				invocation -> "-" + invocation.getArgument(0, BigDecimal.class).toPlainString());

		owner = mock(Player.class);
		when(owner.isOnline()).thenReturn(true);

		wanted  = new Wanted(plugin, 1, 5);
		context = new FakeContext(wanted);
		stars   = new WantedStars(plugin, settings);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("raise adds the stars with their origin and starts a SYNC clock at the new level's interval")
	void raise_addsStarsWithTheOrigin_andStartsASyncClock() {
		mainThread();
		ownedAt(0);
		when(settings.isTimerMultiplierEnabled()).thenReturn(true);
		when(settings.getTimerMultiplierAmount()).thenReturn(2.0);

		int added = stars.raise(context, 2, WantedCause.CRIME);

		assertEquals(2, added);
		assertEquals(2, wanted.getLevel());
		assertEquals(WantedCause.CRIME, only(WantedLevelChangeEvent.class).getCause());
		assertEquals(WantedCause.CRIME, only(WantedStartEvent.class).getCause());
		// 120 s * 2.0 ^ 2 = 480 s = 9600 ticks
		verify(bukkit.scheduler()).runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(9600L));
		verify(bukkit.scheduler(), never()).runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class),
		                                                               anyLong(), anyLong());
	}

	@Test
	@DisplayName("with Repeating_Timer disabled a raise still adds stars but starts no clock")
	void raise_timerDisabled_startsNoClock() {
		mainThread();
		ownedAt(0);
		when(settings.isTimerEnabled()).thenReturn(false);

		assertEquals(1, stars.raise(context, 1, WantedCause.SIGN));

		assertEquals(1, wanted.getLevel());
		assertNoClockStarted();
	}

	@Test
	@DisplayName("a raise at the maximum adds nothing and fires no change, yet (as handleWanted today) restarts the clock")
	void raise_atMaximum_returnsZero() {
		mainThread();
		ownedAt(5);

		assertEquals(0, stars.raise(context, 1, WantedCause.CRIME));

		assertEquals(5, wanted.getLevel());
		assertTrue(events.isEmpty(), "no level change, so no event");
		verify(bukkit.scheduler()).runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong());
	}

	@Test
	@DisplayName("a cancelled level change adds nothing and starts no clock")
	void raise_cancelledChange_returnsZeroAndStartsNoClock() {
		mainThread();
		ownedAt(1);
		cancelChanges = true;

		assertEquals(0, stars.raise(context, 2, WantedCause.CRIME));

		assertEquals(1, wanted.getLevel());
		assertNoClockStarted();
	}

	@Test
	@DisplayName("drop lowers with its cause and sends the decreased message for the NEW level")
	void drop_lowersWithTheCause_andSendsTheDecreasedMessageForTheNewLevel() {
		mainThread();
		ownedAt(3);

		assertEquals(1, stars.drop(context, 1, WantedCause.EVASION));

		assertEquals(2, wanted.getLevel());
		assertEquals(WantedCause.EVASION, only(WantedLevelChangeEvent.class).getCause());
		assertEquals(List.of("decreased 2 " + Wanted.buildStars(2, 5)), context.messages);
	}

	@Test
	@DisplayName("each dropped star costs Amount x Multiplier ^ (level before it falls), summed into one withdraw")
	void drop_chargesAmountTimesMultiplierPowLevelPerStar() {
		mainThread();
		when(settings.getTakeMoneyAmount()).thenReturn(new BigDecimal("50"));
		when(settings.getTakeMoneyMultiplier()).thenReturn(5.0);

		ownedAt(2);
		assertEquals(1, stars.drop(context, 1, WantedCause.DECAY));
		assertEquals(1, wanted.getLevel());

		wanted.setLevel(2);
		assertEquals(2, stars.drop(context, 2, WantedCause.EVASION));
		assertEquals(0, wanted.getLevel());

		assertEquals(2, context.withdrawals.size(), "one withdraw per drop, not per star");
		assertMoney("1250", context.withdrawals.get(0));   // 50 * 5^2
		assertMoney("1500", context.withdrawals.get(1));   // 50 * 5^2 + 50 * 5^1
		assertTrue(context.messages.contains("-1250.00"), context.messages.toString());
		assertTrue(context.messages.contains("-1500.00"), context.messages.toString());
	}

	@Test
	@DisplayName("a cancelled level change charges nothing and sends nothing")
	void drop_cancelledChange_chargesNothing() {
		mainThread();
		when(settings.getTakeMoneyAmount()).thenReturn(new BigDecimal("50"));
		when(settings.getTakeMoneyMultiplier()).thenReturn(5.0);
		ownedAt(2);
		cancelChanges = true;

		int dropped = stars.drop(context, 1, WantedCause.DECAY);

		assertTrue(context.withdrawals.isEmpty(), "the star did not fall, so nothing is charged");
		assertTrue(context.messages.isEmpty());
		assertEquals(0, dropped);
		assertEquals(2, wanted.getLevel());
	}

	@Test
	@DisplayName("a drop to zero stops the decay clock")
	void drop_toZero_stopsTheClock() {
		mainThread();
		ownedAt(1);
		Timer clock = stars.startDecayClock(context);
		assertNotNull(clock);

		assertEquals(1, stars.drop(context, 1, WantedCause.EVASION));

		assertEquals(0, wanted.getLevel());
		assertNull(wanted.getRepeatingTimer());
		verify(clockTask).cancel();
	}

	@Test
	@DisplayName("drop called off the main thread re-schedules itself there with the same cause")
	void drop_offMainThread_hopsToTheMainThread() {
		bukkit.statics().when(Bukkit::isPrimaryThread).thenReturn(false, true);
		ownedAt(3);

		assertEquals(0, stars.drop(context, 1, WantedCause.DECAY), "the off-thread call itself drops nothing");

		verify(bukkit.scheduler()).runTask(eq(plugin), any(Runnable.class));
		assertEquals(2, wanted.getLevel(), "the main-thread re-run dropped the star");
		assertEquals(WantedCause.DECAY, only(WantedLevelChangeEvent.class).getCause());
		assertEquals(1, context.messages.size(), "the message is sent once, by the re-run");
	}

	@Test
	@DisplayName("restore off the main thread hops, sets the level with RESTORE and starts a sync clock")
	void restore_offMainThread_setsRestoreLevel_andStartsTheClock() {
		bukkit.statics().when(Bukkit::isPrimaryThread).thenReturn(false, true);
		ownedAt(0);

		stars.restore(context, 3);

		verify(bukkit.scheduler()).runTask(eq(plugin), any(Runnable.class));
		assertEquals(3, wanted.getLevel());
		assertEquals(WantedCause.RESTORE, only(WantedStartEvent.class).getCause());
		verify(bukkit.scheduler()).runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong());
		verify(bukkit.scheduler(), never()).runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class),
		                                                               anyLong(), anyLong());
	}

	@Test
	@DisplayName("restoring level 0 starts no clock")
	void restore_zero_startsNoClock() {
		mainThread();
		ownedAt(0);

		stars.restore(context, 0);

		assertEquals(0, wanted.getLevel());
		assertNoClockStarted();
	}

	@Test
	@DisplayName("decay is handled only while a policy is installed, the wanted has an owner and the policy says so")
	void isDecayHandled_onlyWithAPolicyAndAnOwner() {
		assertFalse(stars.isDecayHandled(wanted), "no policy");

		stars.installDecayPolicy((player, w) -> player == owner && w == wanted);
		assertFalse(stars.isDecayHandled(wanted), "no owner");

		wanted.setOwner(owner);
		assertTrue(stars.isDecayHandled(wanted));

		stars.installDecayPolicy(null);
		assertFalse(stars.isDecayHandled(wanted), "uninstalled");
	}

	@Test
	@DisplayName("the star chat line shows unless the installed supplier answers true")
	void suppressStarChat_supplierTrue_hidesTheChatLine() {
		assertTrue(stars.isStarChat());

		boolean[] hud = {false};
		stars.suppressStarChat(() -> hud[0]);
		assertTrue(stars.isStarChat());

		hud[0] = true;
		assertFalse(stars.isStarChat());

		stars.suppressStarChat(null);
		assertTrue(stars.isStarChat());
	}

	private void mainThread() {
		bukkit.statics().when(Bukkit::isPrimaryThread).thenReturn(true);
	}

	/** Sets the level while ownerless (no events, no thread check), then attaches the owner. */
	private void ownedAt(int level) {
		wanted.setLevel(level);
		wanted.setOwner(owner);
	}

	private <T extends Event> T only(Class<T> type) {
		List<T> matching = events.stream().filter(type::isInstance).map(type::cast).toList();
		assertEquals(1, matching.size(), "exactly one " + type.getSimpleName() + " in " + events);
		return matching.get(0);
	}

	private void assertNoClockStarted() {
		verify(bukkit.scheduler(), never()).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
		verify(bukkit.scheduler(), never()).runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class),
		                                                               anyLong(), anyLong());
	}

	private static void assertMoney(String expected, BigDecimal actual) {
		assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
	}

	/** Records withdrawals and messages; the wallet is bottomless, so withdraw returns what was asked. */
	private static final class FakeContext implements WantedContext {

		private final Wanted           wanted;
		private final List<BigDecimal> withdrawals = new ArrayList<>();
		private final List<String>     messages    = new ArrayList<>();

		private FakeContext(Wanted wanted) {
			this.wanted = wanted;
		}

		@Override
		public Wanted getWanted() {
			return wanted;
		}

		@Override
		public BigDecimal withdraw(BigDecimal requestedAmount) {
			withdrawals.add(requestedAmount);
			return requestedAmount;
		}

		@Override
		public void sendMessage(String message) {
			messages.add(message);
		}
	}
}
