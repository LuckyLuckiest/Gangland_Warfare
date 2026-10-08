package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.listener.detainment.CopListener;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link CopListener} quit handling (0.16.1 T-187): a player who quits while wanted takes his squad with him instead of
 * leaving it orphaned in the world.
 */
@DisplayName("CopListener - quitting while wanted despawns the squad")
class CopListenerQuitTest {

	private CopManagerFixture fx;

	@BeforeEach
	void setUp() {
		fx = new CopManagerFixture();
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	@Test
	@DisplayName("a quit while wanted removes the player's squad (0.16.1 T-187)")
	void quit_despawnsGroup() {
		Player player = fx.player(10, 10);
		fx.manager.onWantedStart(player, CopManagerFixture.wanted(2));
		fx.manager.groupFor(player.getUniqueId()).add(fx.cop(CopState.PURSUING, 0, 0));

		new CopListener(fx.manager).onPlayerQuit(new PlayerQuitEvent(player, "quit"));

		assertNull(fx.manager.groupFor(player.getUniqueId()));
	}
}
