package org.luckyraven.gangland.npcshops.holdup;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.gangland.npcshops.trader.mood.MoodService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * P-CRIME-2. Timeline (all in ms, polled every 250 ms = 5 ticks): the aim must be held 1 s before a session starts;
 * Fill_Seconds / Alarm_Seconds count from that session start. So with aim from t=0 the session starts at t=1000, the
 * disliked alarm trips at t=5000 and the full 10 s fill (alarm + payout) lands at t=11000.
 */
@DisplayName("HoldUpService - aim a Bartizan weapon at a trader (P-CRIME-2)")
class HoldUpServiceTest {

	private static final UUID   TRADER_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID   TRADER_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final UUID   PLAYER   = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
	private static final String FILLING  = "Filling {seconds}s";
	private static final String DONE     = "Done {take}";
	private static final String ABORTED  = "ABORTED";
	private static final String CLOSED   = "CLOSED";

	private AtomicLong   clock;
	private LongPredicate aimWindow;
	private LongPredicate weaponWindow;
	private LongPredicate onlineWindow;
	private LongPredicate listedWindow;
	private AtomicInteger resolverCalls;
	private HoldUpSettings settings;

	private Player        player;
	private Location      locA;
	private Location      locB;
	private HoldUpTarget  traderA;
	private HoldUpTarget  traderB;
	private HoldUpTarget  aimedTarget;
	private MoodService   mood;
	private CrimeService  crimes;
	private HoldUpPayout  payout;
	private BiConsumer<Player, String> actionBar;
	private List<String>  debugLines;
	private HoldUpService service;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		clock         = new AtomicLong();
		aimWindow     = t -> true;
		weaponWindow  = t -> true;
		onlineWindow  = t -> true;
		listedWindow  = t -> true;
		resolverCalls = new AtomicInteger();
		settings      = spec(true);

		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(PLAYER);
		when(player.getName()).thenReturn("Alice");
		when(player.isOnline()).thenAnswer(inv -> onlineWindow.test(clock.get()));

		locA    = mock(Location.class);
		locB    = mock(Location.class);
		traderA = new HoldUpTarget(TRADER_A, "Clerk", locA);
		traderB = new HoldUpTarget(TRADER_B, "Barkeep", locB);
		aimedTarget = traderA;

		mood   = mock(MoodService.class);
		when(mood.getMood(any(), any())).thenReturn(0.5D);
		crimes = mock(CrimeService.class);
		payout = mock(HoldUpPayout.class);
		when(payout.pay(player)).thenReturn(new BigDecimal("345.67"));
		actionBar  = mock(BiConsumer.class);
		debugLines = new ArrayList<>();

		service = newService(() -> settings);
	}

	private HoldUpSettings spec(boolean enable) {
		return new HoldUpSettings(enable, 6.0, 10.0, 10, 10, 0.2, 4, 200.0, 600.0, 300, FILLING, DONE, ABORTED,
		                          CLOSED);
	}

	private HoldUpService newService(Supplier<HoldUpSettings> loader) {
		Supplier<Collection<? extends Player>> online = () -> listedWindow.test(clock.get()) ? List.of(player)
		                                                                                    : List.of();
		return new HoldUpService(null, loader, online, p -> weaponWindow.test(clock.get()), (p, st) -> {
			resolverCalls.incrementAndGet();
			return aimWindow.test(clock.get()) ? aimedTarget : null;
		}, mood, crimes, payout, clock::get, actionBar, debugLines::add);
	}

	/** Polls every 250 ms from {@code from} to {@code to} inclusive. */
	private void run(long from, long to) {
		for (long t = from; t <= to; t += 250) {
			clock.set(t);
			service.tick();
		}
	}

	@Test
	@DisplayName("aiming for one second starts a session, half a second does not")
	void aimingOneSecondStartsSession() {
		run(0, 750);
		assertFalse(service.hasSession(PLAYER));
		run(1000, 1000);
		assertTrue(service.hasSession(PLAYER));
		assertTrue(debugLines.stream().anyMatch(l -> l.contains("HOLDUP Alice trader=Clerk state=start")),
		           debugLines::toString);
	}

	@Test
	@DisplayName("a full hold at mood 0.5 alarms once and pays once, both at the 10 s fill")
	void fullHoldPaysAndAlarmsAtTen() {
		run(0, 10750);
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		verify(payout, never()).pay(any());

		run(11000, 11000);
		verify(crimes, times(1)).commit(player, Crimes.STORE_ROBBERY, locA, false, 1);
		verify(payout, times(1)).pay(player);

		run(11250, 20000);
		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		verify(payout, times(1)).pay(any());
		assertFalse(service.hasSession(PLAYER));
	}

	@Test
	@DisplayName("a disliked trader (mood 0.1) trips the alarm at 4 s even if the aim stops, and nothing is paid")
	void dislikedTraderAlarmsAtFour() {
		when(mood.getMood(TRADER_A, PLAYER)).thenReturn(0.1D);
		aimWindow = t -> t <= 6000; // session start 1000, alarm at 5000, aim stops after 6000

		run(0, 4750);
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		run(5000, 5000);
		verify(crimes, times(1)).commit(player, Crimes.STORE_ROBBERY, locA, false, 1);

		run(5250, 20000);
		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		verify(payout, never()).pay(any());
		assertFalse(service.hasSession(PLAYER));
	}

	@Test
	@DisplayName("a disliked trader held for the full 10 s still pays: alarm at 4 s, take at 10 s, no Closed refusal")
	void dislikedFullHoldStillPays() {
		when(mood.getMood(TRADER_A, PLAYER)).thenReturn(0.1D);

		run(0, 10750);
		verify(player, never()).sendMessage(contains(CLOSED));
		verify(crimes, times(1)).commit(player, Crimes.STORE_ROBBERY, locA, false, 1);

		run(11000, 11000);
		verify(payout, times(1)).pay(player);
		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("mood exactly at the Dislike_Mood threshold counts as disliked")
	void moodAtThresholdAlarmsEarly() {
		when(mood.getMood(TRADER_A, PLAYER)).thenReturn(0.2D);
		run(0, 5000);
		verify(crimes, times(1)).commit(player, Crimes.STORE_ROBBERY, locA, false, 1);
	}

	@Test
	@DisplayName("a trader the player is on good terms with does not alarm early")
	void friendlyTraderDoesNotAlarmEarly() {
		when(mood.getMood(TRADER_A, PLAYER)).thenReturn(0.21D);
		run(0, 10750);
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("looking away for a second at 6 s aborts: no alarm, no take, aborted message")
	void lookingAwayAbortsWithoutTake() {
		aimWindow = t -> t <= 7000; // session elapsed 6 s at the last aimed poll
		run(0, 8000);
		assertFalse(service.hasSession(PLAYER));
		verify(player, times(1)).sendMessage(contains(ABORTED));

		run(8250, 20000);
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		verify(payout, never()).pay(any());
		assertTrue(debugLines.stream().anyMatch(l -> l.contains("state=abort")), debugLines::toString);
	}

	@Test
	@DisplayName("a half-second glance away does not abort the session")
	void briefLookAwayKeepsSession() {
		aimWindow = t -> t < 4000 || t > 4500;
		run(0, 11000);
		verify(crimes, times(1)).commit(player, Crimes.STORE_ROBBERY, locA, false, 1);
		verify(payout, times(1)).pay(player);
	}

	@Test
	@DisplayName("switching the aim to another trader aborts the first fill and starts a new hold on the second")
	void switchingTraderAbortsTheFirst() {
		clock.set(0);
		run(0, 4750);
		aimedTarget = traderB;
		run(5000, 12000);
		verify(payout, never()).pay(any()); // A aborted at ~6 s; B's own fill is not done until 16 s
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		assertTrue(service.hasSession(PLAYER));

		run(12250, 16000);
		verify(crimes, times(1)).commit(player, Crimes.STORE_ROBBERY, locB, false, 1);
		verify(payout, times(1)).pay(player);
	}

	@Test
	@DisplayName("dropping the weapon counts as looking away")
	void droppingTheWeaponAborts() {
		weaponWindow = t -> t <= 5000;
		run(0, 20000);
		verify(payout, never()).pay(any());
		assertFalse(service.hasSession(PLAYER));
	}

	@Test
	@DisplayName("a second hold-up on the same trader 120 s later is refused with the closed message")
	void traderCooldownRefuses() {
		aimWindow = t -> t <= 11000 || t >= 131000;
		run(0, 135000);
		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		verify(payout, times(1)).pay(any());
		verify(player, times(1)).sendMessage(contains(CLOSED));
		assertFalse(service.hasSession(PLAYER));
	}

	@Test
	@DisplayName("a robbed trader refuses for 300 s and is open for business again after that")
	void cooldownExpiresAfterThreeHundredSeconds() {
		aimWindow = t -> t <= 11000 || t >= 312000;
		run(0, 325000);
		verify(payout, times(2)).pay(player);
		verify(crimes, times(2)).commit(player, Crimes.STORE_ROBBERY, locA, false, 1);
	}

	@Test
	@DisplayName("the cooldown is per trader: another clerk can still be robbed right away")
	void cooldownIsPerTrader() {
		aimWindow = t -> true;
		run(0, 11000);
		aimedTarget = traderB;
		run(11250, 24000);
		verify(payout, times(2)).pay(player);
		verify(crimes, times(1)).commit(player, Crimes.STORE_ROBBERY, locB, false, 1);
	}

	@Test
	@DisplayName("a tripped alarm also starts the trader cooldown, so an abort does not allow an instant repeat")
	void alarmStartsTheCooldown() {
		when(mood.getMood(TRADER_A, PLAYER)).thenReturn(0.1D);
		aimWindow = t -> t <= 6000 || t >= 20000;
		run(0, 28000);
		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		verify(player, times(1)).sendMessage(contains(CLOSED));
	}

	@Test
	@DisplayName("Enable false does nothing: no session, no resolver lookup, no crime, no payout")
	void disabledDoesNothing() {
		settings = spec(false);
		service  = newService(() -> settings);
		run(0, 20000);
		assertFalse(service.hasSession(PLAYER));
		assertEquals(0, resolverCalls.get());
		verifyNoInteractions(crimes, payout);
	}

	@Test
	@DisplayName("no Bartizan weapon in hand (or no Bartizan) does nothing")
	void noWeaponDoesNothing() {
		weaponWindow = t -> false;
		run(0, 20000);
		assertFalse(service.hasSession(PLAYER));
		assertEquals(0, resolverCalls.get());
		verifyNoInteractions(crimes, payout);
	}

	@Test
	@DisplayName("the service only ever reads the mood, never changes it")
	void moodIsNeverChanged() {
		run(0, 11000);
		assertTrue(mockingDetails(mood).getInvocations().size() > 0);
		mockingDetails(mood).getInvocations().forEach(
				inv -> assertEquals("getMood", inv.getMethod().getName(), "mood must be read-only"));
	}

	@Test
	@DisplayName("a player who goes offline mid-hold-up drops the session without a take")
	void offlinePlayerAborts() {
		onlineWindow = t -> t < 5000;
		run(0, 20000);
		verify(payout, never()).pay(any());
		assertFalse(service.hasSession(PLAYER));
	}

	@Test
	@DisplayName("a player missing from the online list drops the session without a take")
	void playerLeavingTheOnlineListAborts() {
		listedWindow = t -> t < 5000;
		run(0, 20000);
		verify(payout, never()).pay(any());
		assertFalse(service.hasSession(PLAYER));
	}

	@Test
	@DisplayName("reload applies new settings to the next poll")
	void reloadPicksUpNewSettings() {
		settings = new HoldUpSettings(true, 6.0, 10.0, 3, 3, 0.2, 2, 200.0, 600.0, 300, FILLING, DONE, ABORTED,
		                              CLOSED); // swapped in WITHOUT reload: the service must keep its cached copy
		run(0, 4000); // not reloaded yet: the cached 10 s settings still apply
		verify(payout, never()).pay(any());
		service.reload();
		run(4250, 4250); // cached 3 s settings: the live session (started 1000) is already past 3 s
		verify(payout, times(1)).pay(player);
	}

	@Test
	@DisplayName("clear drops live sessions and forgets cooldowns")
	void clearDropsSessionsAndCooldowns() {
		run(0, 2000);
		assertTrue(service.hasSession(PLAYER));
		service.clear();
		assertFalse(service.hasSession(PLAYER));

		run(2250, 14000); // aim never stopped: a fresh session starts and finishes
		verify(payout, times(1)).pay(player);
		service.clear();
		clock.set(14250);
		service.tick();
		run(14500, 16000);
		assertTrue(service.hasSession(PLAYER), "cleared cooldown lets the same trader be robbed again");
	}

	@Test
	@DisplayName("the done message carries the take")
	void doneMessageCarriesTheTake() {
		run(0, 11000);
		verify(player, times(1)).sendMessage(contains("345.67"));
	}

	@Test
	@DisplayName("the action bar shows the filling text while a session runs")
	void actionBarShowsFilling() {
		run(0, 3000);
		verify(actionBar, org.mockito.Mockito.atLeastOnce()).accept(eq(player), contains("Filling 8s"));
		verify(actionBar, never()).accept(any(), contains("{seconds}"));
	}

	@Test
	@DisplayName("debug lines follow the HOLDUP contract for alarm and done")
	void debugLinesFollowTheContract() {
		run(0, 11000);
		assertTrue(debugLines.stream().anyMatch(l -> l.contains("HOLDUP Alice trader=Clerk state=alarm")),
		           debugLines::toString);
		assertTrue(debugLines.stream().anyMatch(l -> l.contains("state=done") && l.contains("take=345.67")),
		           debugLines::toString);
	}

}
