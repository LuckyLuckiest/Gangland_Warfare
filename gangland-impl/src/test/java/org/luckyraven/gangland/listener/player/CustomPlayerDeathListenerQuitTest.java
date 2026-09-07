package org.luckyraven.gangland.listener.player;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.support.SettingsFixture;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the quit path of {@link CustomPlayerDeathListener}.
 *
 * <p>Observation #9 (wanted-bounty-combat.md) / WB-09: {@code onPlayerQuit} used to call {@code cleanup(uuid)}
 * only. That drops the registry entry, the countdown task and the saved game mode, but never undoes anything the
 * downed state applied — so a player who logged out while downed was written to {@code player.dat} in the
 * configured downed game mode (spectator by default) with {@code DOWNED_HEALTH} (0.5) health, and rejoined stuck
 * there with no way back short of an operator. The quit handler now restores game mode, flight, health and food
 * before cleaning up.
 *
 * <p>No {@code BukkitStatics} here on purpose: the restore path is deliberately free of scheduler, event and
 * attribute-registry calls, so the whole behaviour is reachable with plain mocks.
 */
@DisplayName("CustomPlayerDeathListener - quitting while downed must not persist the downed state")
class CustomPlayerDeathListenerQuitTest {

	@TempDir
	Path tempDir;

	private Player player;
	private UUID   uuid;

	private CustomPlayerDeathListener listener;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		// Health/Hunger fall back to the shipped defaults (20 / 20) when the Respawn section is absent.
		SettingsFixture.initializeMinimal(tempDir);

		uuid   = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);

		listener = new CustomPlayerDeathListener(mock(Gangland.class),
		                                         mock(UserManager.class),
		                                         mock(WaypointManager.class),
		                                         // JetpackService cannot be instrumented here: its class
		                                         // initialisation pulls in Netty, which is not on the test
		                                         // classpath. The listener null-guards it (line 211) and the
		                                         // quit-restore path never touches it.
		                                         null);
	}

	@AfterEach
	void tearDown() {
		// DownedPlayerRegistry is a process-wide static set (documentation/TESTING.md §4).
		DownedPlayerRegistry.remove(uuid);
	}

	@Test
	@DisplayName("WB-09: a downed player who quits is restored to a playable game mode, health and flight state")
	void quitWhileDowned_restoresGameModeHealthAndFlight() {
		DownedPlayerRegistry.add(uuid);

		listener.onPlayerQuit(quitEvent());

		// No game mode was ever saved for this uuid (the player was marked downed directly), so the restore falls
		// back to SURVIVAL rather than leaving them in the downed game mode.
		verify(player).setGameMode(GameMode.SURVIVAL);
		verify(player).setAllowFlight(false);
		verify(player).setFlying(false);
		verify(player).setHealth(20.0);
		verify(player).setFoodLevel(20);

		assertFalse(DownedPlayerRegistry.isDowned(uuid), "the registry entry must still be cleaned up on quit");
	}

	@Test
	@DisplayName("a player who was not downed is left completely alone on quit")
	void quitWhileNotDowned_touchesNothingOnThePlayer() {
		listener.onPlayerQuit(quitEvent());

		verify(player, never()).setGameMode(any(GameMode.class));
		verify(player, never()).setAllowFlight(anyBoolean());
		verify(player, never()).setFlying(anyBoolean());
		verify(player, never()).setHealth(anyDouble());
		verify(player, never()).setFoodLevel(anyInt());
	}

	/**
	 * A mocked event rather than a real one: {@code PlayerQuitEvent}'s constructors have shifted across the
	 * supported Spigot range, and the handler only ever reads {@code getPlayer()}.
	 */
	private PlayerQuitEvent quitEvent() {
		PlayerQuitEvent event = mock(PlayerQuitEvent.class);
		when(event.getPlayer()).thenReturn(player);

		return event;
	}

}
