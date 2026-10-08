package org.luckyraven.gangland.copsncrooks.npc.police.targeting;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link WantedTargetingManager} (0.16.1 T-187): a player on the post-escape search is a cop target although his wanted
 * record says he is no longer wanted.
 */
@DisplayName("WantedTargetingManager - the post-escape search")
class WantedTargetingManagerTest {

	private BukkitStatics bukkit;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a searching player is found as a target although his wanted record says not wanted (0.16.1 T-187)")
	void searchingPlayer_isFound_withWantedFalse() {
		World  world = mock(World.class);
		Player from  = mock(Player.class);
		when(from.getWorld()).thenReturn(world);
		when(from.getLocation()).thenReturn(new Location(world, 0, 64, 0));

		UUID   id        = UUID.randomUUID();
		Player searching = mock(Player.class);
		when(searching.getUniqueId()).thenReturn(id);
		when(searching.isOnline()).thenReturn(true);
		when(searching.isDead()).thenReturn(false);
		when(searching.getWorld()).thenReturn(world);
		when(searching.getLocation()).thenReturn(new Location(world, 5, 64, 0));
		bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(searching);

		Wanted wanted = mock(Wanted.class);
		when(wanted.isWanted()).thenReturn(false);

		WantedTargetingManager targeting = new WantedTargetingManager();
		targeting.registerWanted(searching, wanted);
		targeting.markSearching(id);

		assertSame(searching, targeting.findBestTarget(from));
	}
}
