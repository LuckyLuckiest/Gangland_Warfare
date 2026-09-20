package org.luckyraven.gangland.listener.player;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.file.configuration.inventory.InventoryRuntimeContext;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.inventory.service.InventoryRegistry;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WS2 G1+G2 fix round 2 (re-review, one-line finding). {@link RemoveAccountListener#onPlayerQuit} used to look up
 * the quitting player's {@code User} record and return early when it was {@code null} — before the async task that
 * cleared {@link InventoryRuntimeContext}'s interim per-player lookup map ever ran. A player whose account lookup
 * fails (a real path — {@code InventoryRuntimeContext.openInventoryForPlayer} itself guards against a null
 * {@code User} the same way) leaked an entry in that map forever, since the CONFIG-phase bean is a singleton that
 * survives reload. Pins that {@code clearPlayer} now runs unconditionally, synchronously, ahead of the
 * {@code user == null} guard — mirroring {@code PlayerInventoryCleanup}'s shape.
 */
@DisplayName("RemoveAccountListener.onPlayerQuit — clears InventoryRuntimeContext's per-player map even when the "
             + "User lookup fails")
class RemoveAccountListenerTest {

	@Test
	@DisplayName("a quitting player with no User record still gets InventoryRuntimeContext.clearPlayer called")
	void onPlayerQuit_userLookupFails_stillClearsInventoryRuntimeContext() {
		Gangland          gangland         = mock(Gangland.class);
		GanglandDatabase  ganglandDatabase = mock(GanglandDatabase.class);
		@SuppressWarnings("unchecked")
		UserManager<Player>        userManager        = mock(UserManager.class);
		@SuppressWarnings("unchecked")
		UserManager<OfflinePlayer> offlineUserManager = mock(UserManager.class);
		InventoryRegistry          inventoryRegistry       = mock(InventoryRegistry.class);
		InventoryRuntimeContext    inventoryRuntimeContext = mock(InventoryRuntimeContext.class);

		Player player = mock(Player.class);
		UUID   uuid   = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(uuid);

		// The account lookup fails - this is exactly the scenario the fix targets.
		when(userManager.getUser(player)).thenReturn(null);

		RemoveAccountListener listener = new RemoveAccountListener(gangland, ganglandDatabase, userManager,
				offlineUserManager, inventoryRegistry, inventoryRuntimeContext);

		PlayerQuitEvent event = mock(PlayerQuitEvent.class);
		when(event.getPlayer()).thenReturn(player);

		listener.onPlayerQuit(event);

		verify(inventoryRuntimeContext).clearPlayer(uuid);
	}

}
