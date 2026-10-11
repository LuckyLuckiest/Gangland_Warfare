package org.luckyraven.gangland.gang;

import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.file.GangRules;
import org.mockito.MockedStatic;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** GR-40: {@code Gang.Enable} is the global switch, {@code Worlds.Disabled} switches single worlds off. */
@DisplayName("GangWorldPolicy - where gangs exist")
class GangWorldPolicyTest {

	private MockedStatic<Settings> settings;
	private GangRules              rules;

	@BeforeEach
	void setUp() {
		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		rules    = mock(GangRules.class);
		when(rules.getDisabledWorlds()).thenReturn(Set.of("world_nether"));
	}

	@AfterEach
	void tearDown() {
		settings.close();
	}

	private static World world(String name) {
		World world = mock(World.class);
		when(world.getName()).thenReturn(name);
		return world;
	}

	@Test
	@DisplayName("globalOff_disabledEverywhere")
	void globalOff_disabledEverywhere() {
		settings.when(Settings::isGangEnabled).thenReturn(false);
		GangWorldPolicy policy = new GangWorldPolicy(rules);

		assertFalse(policy.enabled());
		assertFalse(policy.enabledIn(world("world")));
		assertFalse(policy.enabledIn(null));
	}

	@Test
	@DisplayName("listedWorld_disabled")
	void listedWorld_disabled() {
		settings.when(Settings::isGangEnabled).thenReturn(true);
		GangWorldPolicy policy = new GangWorldPolicy(rules);

		assertTrue(policy.enabled());
		assertFalse(policy.enabledIn(world("world_nether")));
		assertFalse(policy.enabledIn(world("World_Nether")), "world names compare ignoring case");
	}

	@Test
	@DisplayName("otherWorld_enabled")
	void otherWorld_enabled() {
		settings.when(Settings::isGangEnabled).thenReturn(true);

		assertTrue(new GangWorldPolicy(rules).enabledIn(world("world")));
	}

	@Test
	@DisplayName("nullWorld_followsGlobal")
	void nullWorld_followsGlobal() {
		settings.when(Settings::isGangEnabled).thenReturn(true);
		assertTrue(new GangWorldPolicy(rules).enabledIn(null));

		settings.when(Settings::isGangEnabled).thenReturn(false);
		assertFalse(new GangWorldPolicy(rules).enabledIn(null));
	}
}
