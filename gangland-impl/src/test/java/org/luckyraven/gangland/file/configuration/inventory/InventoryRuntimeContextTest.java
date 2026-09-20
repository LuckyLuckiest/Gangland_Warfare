package org.luckyraven.gangland.file.configuration.inventory;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.menu.InventoryBuilder;
import org.luckyraven.gangland.menu.InventoryData;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.menu.condition.ConditionEvaluator;
import org.luckyraven.gangland.menu.multi.ItemSourceProvider;
import org.luckyraven.gangland.inventory.service.InventoryRegistry;
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.keystone.permission.PermissionManager;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WS2 G1+G2 fix round 1 (Opus review F1, orchestrator ruling W38). {@link InventoryRuntimeContext#openInventoryForPlayer}
 * used to look up "is this menu already open for this player" through the shared {@link InventoryRegistry} — but that
 * registry is a superset of what {@code User.inventories} used to isolate (every feature's {@link InventoryHandler}
 * self-registers into it: sign views, wand views, paperwork/bribe views, the car-sign preview, {@code MultiPanelInventory}
 * panels — anything constructed with a non-null {@code Player}, see {@code InventoryHandler.java:82-84}). A foreign
 * handler from a completely different feature whose title key happens to collide with a YAML core-menu name would be
 * "reopened" instead of a fresh core menu being built. Pins that {@link InventoryRuntimeContext} keeps its own
 * per-player, per-name lookup — exactly what {@code User.inventories} was — so a same-key foreign handler in the shared
 * registry is never mistaken for one of the menus this class itself opened.
 */
@DisplayName("InventoryRuntimeContext.openInventoryForPlayer — a same-key foreign handler in the shared registry is not reopened")
class InventoryRuntimeContextTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
	}

	@Test
	@DisplayName("a foreign handler registered under a key equal to a YAML menu name is not reopened - a fresh menu is built instead")
	void foreignHandlerWithCollidingKey_isNotReopened_freshMenuIsBuilt() {
		Gangland gangland = mock(Gangland.class);
		JavaPlugin plugin = gangland;

		Player player = mock(Player.class);
		UUID   uuid   = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(uuid);

		@SuppressWarnings("unchecked")
		User<Player> user = mock(User.class);
		when(user.getUuid()).thenReturn(uuid);
		when(user.getUser()).thenReturn(player);

		@SuppressWarnings("unchecked")
		UserManager<Player> userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);

		// A completely unrelated feature (e.g. a sign view) already has a handler open for this same player, whose
		// title key happens to collide with the core menu name we're about to open ("phone").
		InventoryRegistry sharedRegistry = new InventoryRegistry();
		InventoryHandler  foreignHandler = mock(InventoryHandler.class);
		when(foreignHandler.getTitle()).thenReturn(new NamespacedKey("gangland", "phone"));
		sharedRegistry.registerInventory(uuid, foreignHandler);

		// The definition store has a real YAML-backed "phone" menu registered; its builder is mocked so
		// createInventory(...) returns a controlled fresh handler without running the real slot-render pipeline.
		InventoryDefinitionStore definitionStore = new InventoryDefinitionStore(plugin);
		InventoryData            inventoryData   = mock(InventoryData.class);
		when(inventoryData.isMultiInventory()).thenReturn(false);

		InventoryHandler freshHandler = mock(InventoryHandler.class);
		InventoryBuilder builder      = mock(InventoryBuilder.class);
		when(builder.permission()).thenReturn(null);
		when(builder.inventoryData()).thenReturn(inventoryData);
		when(builder.createInventory(any(), any(), any(), any(), any(), any(), any())).thenReturn(freshHandler);
		definitionStore.inventories().put("phone", builder);

		InventoryRuntimeContext context = new InventoryRuntimeContext(gangland, definitionStore,
				mock(ItemSourceProvider.class), mock(ConditionEvaluator.class), userManager,
				mock(PermissionManager.class), mock(PlaceholderService.class), mock(ItemParser.class),
				sharedRegistry);

		context.openInventoryForPlayer(player, "phone");

		verify(foreignHandler, never()).open(any());
		verify(freshHandler).open(player);
	}

	@Test
	@DisplayName("a menu this context already opened for the player IS reopened on a second call")
	void ownHandler_secondOpen_reopensTheSameInstance() {
		Gangland gangland = mock(Gangland.class);
		JavaPlugin plugin = gangland;

		Player player = mock(Player.class);
		UUID   uuid   = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(uuid);

		@SuppressWarnings("unchecked")
		User<Player> user = mock(User.class);
		when(user.getUuid()).thenReturn(uuid);
		when(user.getUser()).thenReturn(player);

		@SuppressWarnings("unchecked")
		UserManager<Player> userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);

		InventoryRegistry        sharedRegistry  = new InventoryRegistry();
		InventoryDefinitionStore definitionStore = new InventoryDefinitionStore(plugin);
		InventoryData            inventoryData   = mock(InventoryData.class);
		when(inventoryData.isMultiInventory()).thenReturn(false);

		InventoryHandler builtHandler = mock(InventoryHandler.class);
		InventoryBuilder builder      = mock(InventoryBuilder.class);
		when(builder.permission()).thenReturn(null);
		when(builder.inventoryData()).thenReturn(inventoryData);
		when(builder.createInventory(any(), any(), any(), any(), any(), any(), any())).thenReturn(builtHandler);
		definitionStore.inventories().put("phone", builder);

		InventoryRuntimeContext context = new InventoryRuntimeContext(gangland, definitionStore,
				mock(ItemSourceProvider.class), mock(ConditionEvaluator.class), userManager,
				mock(PermissionManager.class), mock(PlaceholderService.class), mock(ItemParser.class),
				sharedRegistry);

		context.openInventoryForPlayer(player, "phone");
		context.openInventoryForPlayer(player, "phone");

		// createInventory only ran once - the second call found the same instance through this context's own
		// per-player lookup and just reopened it.
		verify(builder, times(1)).createInventory(any(), any(), any(), any(), any(), any(), any());
		verify(builtHandler, times(2)).open(player);
	}

}
