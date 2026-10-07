package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.EvasionClock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link EvasionListener#onTeleport}: a long jump by command, plugin or portal marks the evasion track (it rules out a
 * clean break under {@code Drop_Mode: AUTO}); a car dismount or an ender pearl does not.
 */
@DisplayName("EvasionListener")
class EvasionListenerTest {

	private final EvasionClock    clock    = mock(EvasionClock.class);
	private final EvasionListener listener = new EvasionListener(clock);
	private final Player          player   = mock(Player.class);
	private final World           world    = mock(World.class);
	private final Location        from     = new Location(world, 0, 64, 0);

	private void teleport(Location to, TeleportCause cause) {
		listener.onTeleport(new PlayerTeleportEvent(player, from, to, cause));
	}

	@Test
	@DisplayName("a 40-block COMMAND teleport marks the track")
	void commandTeleport40Blocks_marks() {
		teleport(new Location(world, 40, 64, 0), TeleportCause.COMMAND);

		verify(clock).teleported(player);
	}

	@Test
	@DisplayName("a teleport into another world marks the track however short")
	void worldChange_marks() {
		teleport(new Location(mock(World.class), 1, 64, 0), TeleportCause.NETHER_PORTAL);

		verify(clock).teleported(player);
	}

	@Test
	@DisplayName("a 2-block PLUGIN teleport (a car dismount) does not mark the track")
	void pluginTeleport2Blocks_doesNotMark() {
		teleport(new Location(world, 2, 64, 0), TeleportCause.PLUGIN);

		verify(clock, never()).teleported(any());
	}

	@Test
	@DisplayName("an ENDER_PEARL teleport does not mark the track, however far")
	void enderPearl_doesNotMark() {
		teleport(new Location(world, 40, 64, 0), TeleportCause.ENDER_PEARL);

		verify(clock, never()).teleported(any());
	}
}
