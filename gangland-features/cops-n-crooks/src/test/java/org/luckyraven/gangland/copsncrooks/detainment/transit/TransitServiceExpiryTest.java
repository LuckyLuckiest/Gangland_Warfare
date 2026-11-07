package org.luckyraven.gangland.copsncrooks.detainment.transit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentState;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("TransitService - the persisted transit expiry survives (re)scheduling the timer")
class TransitServiceExpiryTest {

	private final UUID                     playerId = UUID.randomUUID();
	private final Player                   player   = mock(Player.class);
	private final JavaPlugin               plugin   = mock(JavaPlugin.class);
	private final DetainmentRegistry       registry = mock(DetainmentRegistry.class);
	private final DetainmentCostsContract  costs    = mock(DetainmentCostsContract.class);
	private final DetainedPlayer           detained = new DetainedPlayer(playerId, null, DetainmentState.HANDCUFFED);
	private final BukkitScheduler          scheduler = mock(BukkitScheduler.class);
	private final TransitService           service;

	TransitServiceExpiryTest() {
		Map<UUID, DetainedPlayer> map = new HashMap<>();
		map.put(playerId, detained);
		when(player.getUniqueId()).thenReturn(playerId);
		when(registry.getDetainedPlayers()).thenReturn(map);
		when(costs.getTransitDelayTicks()).thenReturn(400);
		when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(mock(BukkitTask.class));
		service = new TransitService(plugin, mock(DetainmentService.class), registry, costs);
	}

	@Test
	@DisplayName("T-923: schedule leaves transitExpiresAt set so the next autosave does not write NULL over it")
	void schedule_keepsExpiryInMemory() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

			service.schedule(player);

			assertNotNull(detained.getTransitExpiresAt(), "the expiry persistExpiry just saved was wiped in memory");
		}
	}

	@Test
	@DisplayName("T-923: resumeOnJoin keeps the expiry of the timer it resumes")
	void resumeOnJoin_keepsExpiryInMemory() {
		detained.setTransitExpiresAt(System.currentTimeMillis() + 10_000L);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

			service.resumeOnJoin(player);

			assertNotNull(detained.getTransitExpiresAt(), "the resumed timer's expiry was wiped in memory");
		}
	}

}
