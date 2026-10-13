package org.luckyraven.gangland.civilians.witness;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.events.CivilianCalledInEvent;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.keystone.util.ActionBarManager;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WitnessService.Hooks.live - the production wiring of the injectable seams")
class WitnessServiceHooksLiveTest {

	private final JavaPlugin plugin = mock(JavaPlugin.class);
	private final Player     player = mock(Player.class);

	@Test
	@DisplayName("sight asks the civilian itself: npc.canSee(criminal, range)")
	void sightDelegatesToNpcCanSee() {
		CivilianNpc npc = mock(CivilianNpc.class);
		when(npc.canSee(player, 24.0)).thenReturn(true);

		assertTrue(WitnessService.Hooks.live(plugin).sight().sees(npc, player, 24.0));
		verify(npc).canSee(player, 24.0);
	}

	@Test
	@DisplayName("aim is the geometric WitnessAim")
	void aimIsWitnessAim() {
		assertInstanceOf(WitnessAim.class, WitnessService.Hooks.live(plugin).aim());
	}

	@Test
	@DisplayName("events go through Bukkit.getPluginManager().callEvent")
	void eventsGoThroughPluginManager() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			PluginManager plugins = mock(PluginManager.class);
			bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
			Event event = mock(CivilianCalledInEvent.class);

			WitnessService.Hooks.live(plugin).events().accept(event);

			verify(plugins).callEvent(event);
		}
	}

	@Test
	@DisplayName("the notifier is ActionBarManager.send(plugin, player, text, ticks)")
	void notifierIsActionBar() {
		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			WitnessService.Hooks.live(plugin).notifier().accept(player, "hello");

			bar.verify(() -> ActionBarManager.send(org.mockito.ArgumentMatchers.eq(plugin), org.mockito.ArgumentMatchers.eq(player),
			                                         org.mockito.ArgumentMatchers.eq("hello"), org.mockito.ArgumentMatchers.anyLong()));
		}
	}

	@Test
	@DisplayName("the clock is the wall clock")
	void clockIsWallClock() {
		long before = System.currentTimeMillis();
		long now    = WitnessService.Hooks.live(plugin).clock().getAsLong();

		assertTrue(Math.abs(now - before) < 1000L, "clock " + now + " vs " + before);
	}
}
