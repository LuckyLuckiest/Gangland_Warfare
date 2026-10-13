package org.luckyraven.gangland.copsncrooks.detainment.breakfree;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksModuleConfig;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.copsncrooks.detainment.message.DetainmentMessageContract;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleaseReason;
import org.luckyraven.gangland.copsncrooks.detainment.sound.DetainmentSoundContract;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.util.ActionBarManager;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins that breaking free of handcuffs is a Resisting_Arrest crime, committed before the release, and (0.21.0) the
 * lockpick weighting and snap rules.
 */
@DisplayName("BreakFreeService")
class BreakFreeServiceTest {

	private DetainmentService         detainment;
	private DetainmentCostsContract   costs;
	private DetainmentMessageContract messages;
	private ReleasePipeline           pipeline;
	private CrimeService              crimes;
	private Player                    player;
	private Location                  location;
	private BreakFreeService          service;
	private WantedMessages            wantedMessages;
	private double                    roll;
	private AtomicBoolean             holdsPick;
	private AtomicInteger             consumed;

	@BeforeEach
	void setUp() {
		detainment = mock(DetainmentService.class);
		costs      = mock(DetainmentCostsContract.class);
		messages   = mock(DetainmentMessageContract.class);
		pipeline   = mock(ReleasePipeline.class);
		crimes     = mock(CrimeService.class);
		player     = mock(Player.class);
		location   = mock(Location.class);

		when(detainment.isHandcuffed(player)).thenReturn(true);
		when(costs.getBreakFreeResetWindowTicks()).thenReturn(40);
		when(messages.breakFreeSuccessTitle()).thenReturn("");
		when(messages.breakFreeSuccessSubtitle()).thenReturn("");
		when(messages.breakFreeProgressActionBar(anyInt(), anyInt())).thenReturn("");
		when(player.getLocation()).thenReturn(location);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());

		wantedMessages = mock(WantedMessages.class);
		when(wantedMessages.format(any(WantedMessages.Key.class), anyMap())).thenReturn("snapped-line");
		roll      = 0.5;
		holdsPick = new AtomicBoolean(false);
		consumed  = new AtomicInteger();

		DoubleSupplier random = () -> roll;
		service = new BreakFreeService(mock(JavaPlugin.class), detainment, costs, messages, pipeline,
		                               mock(DetainmentSoundContract.class), crimes, wantedMessages, random);
		service.pickFinder   = p -> holdsPick.get();
		service.pickConsumer = p -> {
			consumed.incrementAndGet();
			return true;
		};
	}

	private void tap(int times) {
		try (BukkitStatics bukkit = BukkitStatics.install();
		     MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			for (int i = 0; i < times; i++) {
				service.registerTap(player);
			}
		}
	}

	private void withPick(int required, int weight, double chance) {
		when(costs.getBreakFreeTapsRequired()).thenReturn(required);
		when(costs.getLockpickTapWeight()).thenReturn(weight);
		when(costs.getLockpickSnapChance()).thenReturn(chance);
		holdsPick.set(true);
	}

	@Test
	@DisplayName("a successful break commits Resisting_Arrest before the release")
	void successfulBreak_commitsResistingArrest_beforeTheRelease() {
		when(costs.getBreakFreeTapsRequired()).thenReturn(1);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			service.registerTap(player);
		}

		InOrder order = inOrder(crimes, pipeline);
		order.verify(crimes).commit(player, Crimes.RESISTING_ARREST, location);
		order.verify(pipeline).release(player, ReleaseReason.BREAK_FREE);
	}

	@Test
	@DisplayName("an unfinished break commits nothing")
	void unfinishedBreak_commitsNothing() {
		when(costs.getBreakFreeTapsRequired()).thenReturn(3);

		try (BukkitStatics bukkit = BukkitStatics.install(); MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			service.registerTap(player);
		}

		verifyNoInteractions(crimes);
	}

	@Test
	@DisplayName("P-PICK-3: no pick, one per tap (4 required: 3 taps hold, the 4th frees)")
	void noPick_onePerTap() {
		when(costs.getBreakFreeTapsRequired()).thenReturn(4);
		when(costs.getLockpickTapWeight()).thenReturn(2);
		when(costs.getLockpickSnapChance()).thenReturn(1.0);

		tap(3);
		verify(pipeline, never()).release(player, ReleaseReason.BREAK_FREE);
		tap(1);
		verify(pipeline).release(player, ReleaseReason.BREAK_FREE);
		assertEquals(0, consumed.get());
	}

	@Test
	@DisplayName("P-PICK-1: a pick with weight 2 frees on the 2nd tap of 4 required")
	void withPick_weightTwo_halfTheTaps() {
		withPick(4, 2, 0.0);

		tap(1);
		verify(pipeline, never()).release(player, ReleaseReason.BREAK_FREE);
		tap(1);
		verify(pipeline).release(player, ReleaseReason.BREAK_FREE);
	}

	@Test
	@DisplayName("P-PICK-1: 25 required with weight 2 frees on tap 13, not 12")
	void withPick_twentyFiveRequired_thirteenTaps() {
		withPick(25, 2, 0.0);

		tap(12);
		verify(pipeline, never()).release(player, ReleaseReason.BREAK_FREE);
		tap(1);
		verify(pipeline).release(player, ReleaseReason.BREAK_FREE);
	}

	@Test
	@DisplayName("P-PICK-1: a weight below 1 still counts one per tap")
	void withPick_weightZero_countsOne() {
		withPick(3, 0, 0.0);

		tap(2);
		verify(pipeline, never()).release(player, ReleaseReason.BREAK_FREE);
		verify(messages).breakFreeProgressActionBar(2, 3);
		tap(1);
		verify(pipeline).release(player, ReleaseReason.BREAK_FREE);
	}

	@Test
	@DisplayName("P-PICK-2: a roll under the chance consumes exactly one pick and tells the player")
	void snapConsumesOnePick() {
		withPick(10, 2, 0.05);
		roll = 0.01;

		tap(1);

		assertEquals(1, consumed.get());
		verify(wantedMessages).format(WantedMessages.Key.LOCKPICK_SNAPPED, Map.of());
		verify(player).sendMessage("snapped-line");
	}

	@Test
	@DisplayName("P-PICK-2: a roll at or above the chance keeps the pick, weight still applies")
	void noSnapAboveChance() {
		withPick(4, 2, 0.05);
		roll = 0.5;

		tap(2);

		assertEquals(0, consumed.get());
		verify(player, never()).sendMessage("snapped-line");
		verify(pipeline).release(player, ReleaseReason.BREAK_FREE);
	}

	@Test
	@DisplayName("P-PICK-2: chance 0.0 never snaps even on a 0.0 roll")
	void zeroChance_neverSnaps() {
		withPick(10, 2, 0.0);
		roll = 0.0;

		tap(3);

		assertEquals(0, consumed.get());
		verify(messages).breakFreeProgressActionBar(6, 10);
	}

	@Test
	@DisplayName("P-PICK-2: with no pick left the next tap counts one")
	void lastPickSnaps_nextTapCountsOne() {
		withPick(10, 2, 0.05);
		roll = 0.01;
		service.pickConsumer = p -> {
			consumed.incrementAndGet();
			holdsPick.set(false);
			return true;
		};

		tap(1);
		tap(1);

		verify(messages).breakFreeProgressActionBar(2, 10);
		verify(messages).breakFreeProgressActionBar(3, 10);
		assertEquals(1, consumed.get());
	}

	@Test
	@DisplayName("P-PICK-2: no pick held means no roll and no snap line")
	void noPick_noRollNoSnap() {
		when(costs.getBreakFreeTapsRequired()).thenReturn(10);
		when(costs.getLockpickTapWeight()).thenReturn(2);
		when(costs.getLockpickSnapChance()).thenReturn(1.0);
		roll = 0.0;

		tap(3);

		assertEquals(0, consumed.get());
		verify(wantedMessages, never()).format(any(WantedMessages.Key.class), anyMap());
		verify(messages, times(1)).breakFreeProgressActionBar(1, 10);
	}

	@Test
	@DisplayName("P-PICK-2: the tap that snaps the pick still counts its weight and can free")
	void snapOnTheFinishingTap_stillFrees() {
		withPick(2, 2, 0.5);
		roll = 0.1;

		tap(1);

		assertEquals(1, consumed.get());
		verify(pipeline).release(player, ReleaseReason.BREAK_FREE);
	}

	@Test
	@DisplayName("a tap by a player who is not cuffed does not roll or release")
	void notCuffed_ignored() {
		withPick(2, 2, 1.0);
		roll = 0.0;
		when(detainment.isHandcuffed(player)).thenReturn(false);

		tap(2);

		assertEquals(0, consumed.get());
		verify(pipeline, never()).release(any(), any());
	}

	@Test
	@DisplayName("a null player is ignored")
	void nullPlayer_ignored() {
		service.registerTap(null);

		verifyNoInteractions(pipeline);
	}

	@Test
	@DisplayName("the breakFreeService bean method takes WantedMessages")
	void beanMethod_takesWantedMessages() {
		Method bean = Arrays.stream(CopsNCrooksModuleConfig.class.getDeclaredMethods())
		                    .filter(m -> m.getName().equals("breakFreeService")).findFirst().orElse(null);

		assertNotNull(bean);
		assertTrue(Arrays.asList(bean.getParameterTypes()).contains(WantedMessages.class));
		assertEquals(BreakFreeService.class, bean.getReturnType());
	}
}
