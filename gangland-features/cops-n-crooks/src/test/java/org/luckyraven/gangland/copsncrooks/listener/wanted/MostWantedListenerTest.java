package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.wanted.mostwanted.MostWanted;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P-MW-3: a quitting player drops the title. */
@DisplayName("MostWantedListener")
class MostWantedListenerTest {

	@Test
	@DisplayName("PlayerQuitEvent calls quit(id) on the tracker; the handler runs at MONITOR")
	void quitCallsTracker() throws NoSuchMethodException {
		MostWanted mw     = mock(MostWanted.class);
		UUID       id     = UUID.randomUUID();
		Player     player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);

		MostWantedListener listener = new MostWantedListener(mw);
		listener.onQuit(new PlayerQuitEvent(player, "bye"));

		verify(mw).quit(id);
		Method handler = MostWantedListener.class.getMethod("onQuit", PlayerQuitEvent.class);
		assertEquals(EventPriority.MONITOR, handler.getAnnotation(EventHandler.class).priority());
		assertTrue(MostWantedListener.class.isAnnotationPresent(ListenerHandler.class));
	}
}
