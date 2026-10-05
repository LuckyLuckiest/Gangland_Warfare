package org.luckyraven.gangland.core.wanted;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.events.wanted.WantedEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.keystone.timer.Timer;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The executor reuses one {@link WantedEvent} for every timer tick: a listener cancelling one decay tick must not
 * cancel every later one. A tick drops its star through {@link WantedStars#drop} with {@link WantedCause#DECAY}, and
 * does nothing at all while an installed {@link WantedDecayPolicy} handles the player's decay (CONTRACTS C3).
 */
@DisplayName("WantedExecutor - the Repeating_Timer safety-net tick")
class WantedExecutorTest {

	private MockedStatic<Bukkit> bukkit;
	private PluginManager        pluginManager;

	@BeforeEach
	void setUp() {
		pluginManager = mock(PluginManager.class);
		bukkit        = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
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

	@Test
	@DisplayName("while an installed policy handles the decay, a tick changes nothing and fires no event")
	void installedPolicyHandlingDecay_tickChangesNothing_andFiresNoEvent() {
		JavaPlugin     plugin   = mock(JavaPlugin.class);
		Wanted         wanted   = ownedWanted(plugin, 3);
		WantedSettings settings = settings();
		WantedStars    stars    = new WantedStars(plugin, settings);
		stars.installDecayPolicy((player, w) -> true);
		Timer timer = mock(Timer.class);

		new WantedExecutor(plugin, new WantedEvent(false, wanted), context(wanted), settings, stars).execute(timer);

		assertEquals(3, wanted.getLevel());
		verify(pluginManager, never()).callEvent(any());
		verify(timer, never()).stop();
	}

	@Test
	@DisplayName("a tick drops one star through WantedStars with the DECAY cause")
	void tick_dropsThroughWantedStarsWithDecayCause() {
		JavaPlugin     plugin   = mock(JavaPlugin.class);
		Wanted         wanted   = ownedWanted(plugin, 2);
		WantedSettings settings = settings();
		WantedContext  context  = context(wanted);
		List<Event>    events   = new ArrayList<>();
		doAnswer(inv -> events.add(inv.getArgument(0))).when(pluginManager).callEvent(any());

		new WantedExecutor(plugin, new WantedEvent(false, wanted), context, settings,
		                   new WantedStars(plugin, settings)).execute(mock(Timer.class));

		assertEquals(1, wanted.getLevel());
		assertInstanceOf(WantedEvent.class, events.get(0));
		assertEquals(WantedCause.DECAY, assertInstanceOf(WantedLevelChangeEvent.class, events.get(1)).getCause());
		verify(context).sendMessage("1");
	}

	private static Wanted ownedWanted(JavaPlugin plugin, int level) {
		Wanted wanted = new Wanted(plugin, 1, 5);
		wanted.setLevel(level);
		wanted.setOwner(mock(Player.class));
		return wanted;
	}

	private static WantedSettings settings() {
		WantedSettings settings = mock(WantedSettings.class);
		when(settings.getTakeMoneyAmount()).thenReturn(BigDecimal.ZERO);
		when(settings.getWantedDecreasedMessageTemplate()).thenReturn("%level%");
		return settings;
	}

	private static WantedContext context(Wanted wanted) {
		WantedContext context = mock(WantedContext.class);
		when(context.getWanted()).thenReturn(wanted);
		return context;
	}
}
