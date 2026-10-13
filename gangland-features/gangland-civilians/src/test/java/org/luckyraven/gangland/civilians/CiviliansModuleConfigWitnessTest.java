package org.luckyraven.gangland.civilians;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.message.CivilianMessages;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.config.CiviliansConfig;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.witness.WitnessRules;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.gangland.civilians.witness.WitnessTestKit;
import org.luckyraven.gangland.crime.CrimeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CiviliansModuleConfig - the WitnessService bean wiring")
class CiviliansModuleConfigWitnessTest {

	private final WitnessTestKit    kit      = new WitnessTestKit();
	private final CivilianMessages  messages = mock(CivilianMessages.class);
	private final List<String>      notices  = new ArrayList<>();

	/** Hooks that see everything, never aim and record the action-bar text. */
	private final WitnessService.Hooks hooks = new WitnessService.Hooks(() -> 0L, (npc, who, range) -> true,
	                                                                  (who, caller, range, angle) -> false,
	                                                                  event -> {
	                                                                  }, (who, text) -> notices.add(text),
	                                                                  line -> {
	                                                                  });

	private WitnessService bean() {
		return CiviliansModuleConfig.witnessService(kit.civilians, kit.crimes, messages, hooks);
	}

	private static CiviliansConfig config(boolean enabled) {
		WitnessRules rules = new WitnessRules(enabled, 24.0, 8, true, 16.0, 12.0, true, 10);
		return new CiviliansConfig(List.of(), List.of(), Map.of(), Map.of(), true, 20, CiviliansConfig.DEFAULT_SHOUTS,
		                           rules);
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
		when(kit.civilians.getCiviliansConfig()).thenReturn(null);
		Player player = mock(Player.class);
		World  world  = mock(World.class);
		when(player.isOnline()).thenReturn(true);

		assertEquals(0, bean().witness(player, "Car_Theft", new Location(world, 0, 64, 0)));
	}

	@Test
	@DisplayName("the rules are re-read from the loaded config on every call: Enable false -> 0, a reloaded config -> 1")
	void rulesReadFromConfigPerCall() {
		kit.civilian(10, false);
		WitnessService service = bean();

		when(kit.civilians.getCiviliansConfig()).thenReturn(config(false));
		assertEquals(0, service.witness(kit.criminal, "Car_Theft", kit.at));

		when(kit.civilians.getCiviliansConfig()).thenReturn(config(true));
		assertEquals(1, service.witness(kit.criminal, "Car_Theft", kit.at));
	}

	@Test
	@DisplayName("the criminal is shown CivilianMessages.witnessCalling()")
	void messageFromCivilianMessages() {
		when(kit.civilians.getCiviliansConfig()).thenReturn(config(true));
		when(messages.witnessCalling()).thenReturn("&cMSG-FROM-YAML");
		CivilianNpc npc = kit.civilian(10, false);

		assertEquals(1, bean().witness(kit.criminal, "Car_Theft", kit.at));

		assertEquals(List.of("&cMSG-FROM-YAML"), notices);
		assertNotNull(npc.getWitnessCall());
	}
}
