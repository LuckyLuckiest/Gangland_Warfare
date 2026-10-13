package org.luckyraven.gangland.civilians;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.message.CivilianMessages;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.gangland.crime.CrimeService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CiviliansModuleConfig - the WitnessService bean")
class CiviliansModuleConfigWitnessTest {

	private final CivilianService civilians = mock(CivilianService.class);

	private WitnessService bean() {
		return new CiviliansModuleConfig(mock(JavaPlugin.class)).witnessService(civilians, mock(CrimeService.class),
		                                                                         mock(CivilianMessages.class));
	}

	@Test
	@DisplayName("the bean is built, with no running calls")
	void beanBuilt() {
		WitnessService service = bean();

		assertNotNull(service);
		assertTrue(service.activeCalls().isEmpty());
	}

	@Test
	@DisplayName("before civilians.yml is loaded (no config yet) witness() answers 0 instead of throwing")
	void unloadedConfigIsSafe() {
		when(civilians.getCiviliansConfig()).thenReturn(null);
		when(civilians.getActiveNpcs()).thenReturn(List.of());
		Player player = mock(Player.class);
		World  world  = mock(World.class);
		when(player.isOnline()).thenReturn(true);

		assertEquals(0, bean().witness(player, "Car_Theft", new Location(world, 0, 64, 0)));
	}
}
