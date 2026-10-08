package org.luckyraven.gangland.copsncrooks.listener.detainment;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.events.police.ArrestedEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CopListener - arrests")
class CopListenerArrestTest {

	@Test
	@DisplayName("an arrest ends the squad, even at zero stars where no wanted end fires (0.16.1 T-187)")
	void arrested_endsTheSquad() {
		CopManager manager = mock(CopManager.class);
		Player     player  = mock(Player.class);
		UUID       id      = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(id);

		new CopListener(manager).onArrested(new ArrestedEvent(player));

		verify(manager).endSquad(id);
	}
}
