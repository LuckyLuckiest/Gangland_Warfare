package org.luckyraven.gangland.copsncrooks.detainment;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.message.DetainmentMessageContract;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
import org.luckyraven.gangland.copsncrooks.support.FakeRepository;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.util.ActionBarManager;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 0.21.0 fnd-origin (P-ORIGIN-1, B1): {@code DetainmentService.handcuff} records the arrest origin in exactly one
 * save, the one-argument form records CUFFED, and the visuals (inventory close, action bar) still happen once.
 */
@DisplayName("DetainmentService.handcuff - the arrest origin")
class DetainmentServiceOriginTest {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000dd");

	private FakeRepository<DetainedPlayer> repository;
	private DetainmentRegistry             registry;
	private DetainmentService              service;
	private Player                        player;

	@BeforeEach
	void setUp() {
		repository = new FakeRepository<>();
		registry   = new DetainmentRegistry(repository, new JailRegistry());

		DetainmentMessageContract messages = mock(DetainmentMessageContract.class);
		when(messages.handcuffedTitle()).thenReturn("");
		when(messages.handcuffedSubtitle()).thenReturn("");
		when(messages.handcuffedActionBar()).thenReturn("");

		service = new DetainmentService(mock(JavaPlugin.class), registry, mock(JailService.class),
		                                new JailRegistry(), messages, "gangland");

		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(ID);
	}

	@Test
	@DisplayName("handcuff(player, SURRENDERED) writes a HANDCUFFED row with that origin in one save")
	void handcuffWithOrigin_recordsOriginInOneSave() {
		try (BukkitStatics bukkit = BukkitStatics.install(); MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			service.handcuff(player, ArrestOrigin.SURRENDERED);
		}

		assertEquals(DetainmentState.HANDCUFFED, registry.getState(ID));
		assertEquals(ArrestOrigin.SURRENDERED, registry.getDetainedPlayers().get(ID).getOrigin());
		assertEquals(1, repository.saved.size(), "one save, the row already carries the origin");
	}

	@Test
	@DisplayName("handcuff(player) records CUFFED, still in exactly one save")
	void handcuffOneArg_recordsCuffed() {
		try (BukkitStatics bukkit = BukkitStatics.install(); MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			service.handcuff(player);
		}

		assertEquals(ArrestOrigin.CUFFED, registry.getDetainedPlayers().get(ID).getOrigin());
		assertEquals(1, repository.saved.size());
	}

	@Test
	@DisplayName("the visuals are unchanged: inventory closed once, handcuffed action bar sent once for 40 ticks")
	void handcuff_keepsTheVisuals() {
		try (BukkitStatics bukkit = BukkitStatics.install(); MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			service.handcuff(player, ArrestOrigin.CUFFED);

			verify(player, times(1)).closeInventory();
			bar.verify(() -> ActionBarManager.send(any(), eq(player), any(), eq(40L)), times(1));
		}
	}
}
