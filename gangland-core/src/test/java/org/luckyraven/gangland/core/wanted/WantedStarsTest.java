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
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.util.Placeholder;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.timer.Timer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
	@DisplayName("with the charge off (the default) a drop moves no money, whatever Amount is")
	void chargeOff_byDefault_movesNoMoney() {
		mainThread();
		when(settings.getTakeMoneyAmount()).thenReturn(new BigDecimal("50"));
		when(settings.getTakeMoneyMultiplier()).thenReturn(5.0);
		ownedAt(3);

		assertEquals(1, stars.drop(context, 1, WantedCause.DECAY));

		assertTrue(context.withdrawals.isEmpty());
		assertEquals(1, context.messages.size(), "only the decreased line");
	}

	@Test
	@DisplayName("charge on with the default formula charges Amount x Multiplier ^ level, one withdraw per drop")
	void chargeOn_defaultFormula_chargesTodaysPrice() {
		mainThread();
		chargeOn("amount * multiplier ^ wanted");

		ownedAt(2);
		assertEquals(1, stars.drop(context, 1, WantedCause.DECAY));
		wanted.setLevel(5);
		assertEquals(1, stars.drop(context, 1, WantedCause.DECAY));

		assertEquals(2, context.withdrawals.size());
		assertMoney("1250", context.withdrawals.get(0));     // 50 * 5^2
		assertMoney("156250", context.withdrawals.get(1));   // 50 * 5^5
		assertTrue(context.messages.contains("-1250.00"), context.messages.toString());
		assertTrue(context.messages.contains("-156250.00"), context.messages.toString());
	}

	@Test
	@DisplayName("a custom formula sees amount, multiplier and wanted (the level before the star falls)")
	void chargeOn_customFormulaUsesAmountMultiplierAndWanted() {
		mainThread();
		chargeOn("amount * multiplier + wanted");
		ownedAt(2);

		stars.drop(context, 1, WantedCause.DECAY);

		assertMoney("252", context.withdrawals.get(0));   // 50 * 5 + 2, not the 1,250 fallback
	}

	@Test
	@DisplayName("a zero Amount charges nothing and sends no money line")
	void chargeOn_zeroAmount_chargesNothing_andSendsNoMoneyLine() {
		mainThread();
		chargeOn("amount * multiplier ^ wanted");
		when(settings.getTakeMoneyAmount()).thenReturn(BigDecimal.ZERO);
		ownedAt(3);

		assertEquals(1, stars.drop(context, 1, WantedCause.DECAY));

		assertTrue(context.withdrawals.isEmpty());
		assertEquals(1, context.messages.size());
	}

	@Test
	@DisplayName("a broken formula charges the fallback price and the star still drops")
	void chargeOn_brokenFormula_chargesTheFallback_andTheStarStillDrops() {
		mainThread();
		chargeOn("amount * * wanted");

		ownedAt(2);
		assertEquals(1, stars.drop(context, 1, WantedCause.DECAY));
		wanted.setLevel(3);
		assertEquals(1, stars.drop(context, 1, WantedCause.DECAY));

		assertEquals(2, wanted.getLevel());
		assertMoney("1250", context.withdrawals.get(0));    // 50 * 5^2
		assertMoney("6250", context.withdrawals.get(1));    // 50 * 5^3
	}

	@Test
	@DisplayName("a formula that comes out negative falls back")
	void chargeOn_negativeFormula_fallsBack() {
		mainThread();
		chargeOn("0 - amount");
		ownedAt(2);

		stars.drop(context, 1, WantedCause.DECAY);

		assertMoney("1250", context.withdrawals.get(0));
	}

	@Test
	@DisplayName("a User context adds its balance to the formula variables")
	void chargeOn_balanceVariable() {
		mainThread();
		chargeOn("balance * 0.02 * wanted");
		IdentitySettings.bind(new FakeIdentitySettingsContract());
		when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
		Placeholder placeholder = mock(Placeholder.class);
		when(placeholder.convert(any(), any())).thenAnswer(inv -> inv.getArgument(1));
		User<Player> user = new User<>(plugin, owner, placeholder);
		user.getEconomy().setAmount(Currency.of(1000));
		user.getWanted().setLevel(2);
		user.getWanted().setOwner(owner);

		stars.drop(user, 1, WantedCause.DECAY);

		assertMoney("960", user.getEconomy().getAmount());   // 1000 - 1000 * 0.02 * 2
	}

	@Test
	@DisplayName("dropping several stars sums the price of each, withdrawn once")
	void allStarsDrop_sumsThePricePerStar() {
		mainThread();
		chargeOn("amount * multiplier ^ wanted");
		ownedAt(3);

		assertEquals(3, stars.drop(context, 3, WantedCause.EVASION));

		assertEquals(1, context.withdrawals.size());
		assertMoney("7750", context.withdrawals.get(0));   // 50 * (5^3 + 5^2 + 5^1)
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

	private void chargeOn(String formula) {
		when(settings.isTakeMoneyEnabled()).thenReturn(true);
		when(settings.getTakeMoneyFormula()).thenReturn(formula);
		when(settings.getTakeMoneyAmount()).thenReturn(new BigDecimal("50"));
		when(settings.getTakeMoneyMultiplier()).thenReturn(5.0);
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
