package org.luckyraven.gangland.core.wanted;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.events.wanted.WantedEvent;
import org.luckyraven.keystone.timer.Timer;
import org.mockito.MockedStatic;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The executor reuses one {@link WantedEvent} for every timer tick: a listener cancelling one decay tick must not
 * cancel every later one.
 */
@DisplayName("WantedExecutor - a cancelled tick does not stick")
class WantedExecutorTest {

	private MockedStatic<Bukkit> bukkit;
	private PluginManager        pluginManager;

	@BeforeEach
	void setUp() {
		pluginManager = mock(PluginManager.class);
		bukkit        = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("after one cancelled tick, the next uncancelled tick still decrements the level")
	void cancelledTick_thenUncancelledTick_decrements() {
		Wanted wanted = new Wanted(null, 1, 5);
		wanted.setLevel(3);

		WantedEvent   event   = new WantedEvent(false, wanted);
		WantedContext context = mock(WantedContext.class);
		when(context.getWanted()).thenReturn(wanted);
		WantedSettings settings = mock(WantedSettings.class);
		when(settings.getTakeMoneyAmount()).thenReturn(BigDecimal.ZERO);
		when(settings.getWantedDecreasedMessageTemplate()).thenReturn("%level%");

		boolean[] cancelNext = {true};
		doAnswer(inv -> {
			if (cancelNext[0]) ((WantedEvent) inv.getArgument(0)).setCancelled(true);
			return null;
		}).when(pluginManager).callEvent(any());

		WantedExecutor executor = new WantedExecutor(mock(JavaPlugin.class), event, context, settings);
		Timer          timer    = mock(Timer.class);

		executor.execute(timer);
		assertEquals(3, wanted.getLevel(), "the cancelled tick keeps the level");

		cancelNext[0] = false;
		executor.execute(timer);
		assertEquals(2, wanted.getLevel(), "the next tick is not cancelled by the stale flag");
	}
}
