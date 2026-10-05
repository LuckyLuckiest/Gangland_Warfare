package org.luckyraven.gangland.copsncrooks.detainment.breakfree;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.copsncrooks.detainment.message.DetainmentMessageContract;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleaseReason;
import org.luckyraven.gangland.copsncrooks.detainment.sound.DetainmentSoundContract;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.util.ActionBarManager;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins that breaking free of handcuffs is a Resisting_Arrest crime, committed before the release.
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
		when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

		service = new BreakFreeService(mock(JavaPlugin.class), detainment, costs, messages, pipeline,
		                               mock(DetainmentSoundContract.class), crimes);
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
}
