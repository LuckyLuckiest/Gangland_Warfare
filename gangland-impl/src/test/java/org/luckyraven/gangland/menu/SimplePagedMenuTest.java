package org.luckyraven.gangland.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.menu.part.ButtonTags;
import org.luckyraven.keystone.cooldown.CooldownService;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.Menu;
import org.luckyraven.keystone.inventory.chest.ChestMenu;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fix round 1, F4 (second half): {@code SimplePagedMenu} with {@code perPage + 1} items — two pages, next/home/prev
 * head textures threaded from {@link ButtonTags} (fix round 1, F1), Home present only on page 2. Same shape as
 * {@code InventoryParserRoundTripTest.allianceStatPaginationBoundary} (F4's YAML-driven half); this one drives the
 * public {@code SimplePagedMenu.open} entry point and navigates via a real {@code dispatchClick} rather than
 * calling a page-builder directly, since {@code SimplePagedMenu} has no YAML/{@code InventoryRuntimeContext} layer
 * to build a fresh page through.
 */
class SimplePagedMenuTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
		Server server = Bukkit.getServer();
		if (server != null) {
			when(server.getVersion()).thenReturn("git-Paper-1 (MC: 1.21.1)");
		}
		// Force XMaterial's clinit to run now, against the REAL (non-static-mocked) server above. Once this
		// class loads successfully it never re-initializes, so the later BukkitStatics try-with-resources block
		// below (which replaces Bukkit.getServer() with its OWN unstubbed mock — getVersion()/getBukkitVersion()
		// return null there) never gets a chance to be the first toucher and trip XMaterial$Data's regex clinit
		// on a null version string. Same root cause InventoryParserRoundTripTest avoids by touching ItemBuilder
		// outside its own BukkitStatics block before the paginated tests run inside one.
		com.cryptomorin.xseries.XMaterial.STONE.get();
	}

	private static Inventory fakeInventory(InventoryHolder holder, int size) {
		ItemStack[] contents = new ItemStack[size];
		Inventory   inv      = mock(Inventory.class);
		when(inv.getHolder()).thenReturn(holder);
		when(inv.getSize()).thenReturn(size);
		when(inv.getItem(anyInt())).thenAnswer(invocation -> {
			int idx = invocation.getArgument(0);
			return idx >= 0 && idx < contents.length ? contents[idx] : null;
		});
		doAnswer(invocation -> {
			int       idx  = invocation.getArgument(0);
			ItemStack item = invocation.getArgument(1);
			if (idx >= 0 && idx < contents.length) contents[idx] = item;
			return null;
		}).when(inv).setItem(anyInt(), any());
		return inv;
	}

	private static BukkitStatics installBukkit() {
		BukkitStatics bukkit = BukkitStatics.install();
		bukkit.statics().when(Bukkit::getItemFactory).thenReturn(mock(ItemFactory.class));
		bukkit.statics().when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), anyString()))
		      .thenAnswer(invocation -> fakeInventory(invocation.getArgument(0), invocation.getArgument(1)));
		return bukkit;
	}

	@Test
	@DisplayName("perPage+1 items -> two pages; next/home/prev textures from ButtonTags; Home only on page 2")
	void twoPagesWithHomeOnlyOnSecondPage() {
		InventoryService inventoryService = new InventoryService(mock(CooldownService.class));
		Player           player           = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());

		// Empty tags: InventoryBuilder.headItem (shared with SimplePagedMenu, fix round 1 F1) calls
		// ItemBuilder.customHead(tag), which needs com.mojang.authlib (XSkull) — real-server-only, absent from a
		// plain unit-test JVM (documentation/TESTING.md §1 forbids a test dependency to paper over it; the
		// paginated Item_Template round-trip tests hit and document the same gap). customHead no-ops on an empty
		// profile string, so this still exercises the real nav-button structure (slot, presence, click-triggered
		// page swap) without needing skin resolution.
		ButtonTags buttonTags = new ButtonTags("", "", "");

		// SimplePagedMenu's row math: 7 columns, rows = clamp(ceil(itemsCount/7) + 2, 3, 6). 29 items ->
		// ceil(29/7) + 2 = 5 + 2 = 7, clamped to 6 -> the default interior region (rows 1..4, cols 1..7) is
		// 28 slots/page — perPage + 1 = 29 forces exactly 2 pages with a 1-entry remainder on the last page.
		List<ItemStack> items = IntStream.range(0, 29).mapToObj(i -> new ItemStack(Material.STONE)).toList();

		try (BukkitStatics ignored = installBukkit()) {
			InventoryView view = mock(InventoryView.class);
			when(player.getOpenInventory()).thenReturn(view);
			when(view.getTopInventory()).thenReturn(mock(Inventory.class));

			SimplePagedMenu.open(inventoryService, player, items, "&6&lTest List", InventoryBuilder.DEFAULT_FILL_ITEM,
			                     InventoryBuilder.DEFAULT_FILL_NAME, buttonTags);

			Menu tracked = inventoryService.tracker().currentMenuOf(player);
			ChestMenu menu = assertInstanceOf(ChestMenu.class, tracked, "InventoryService must track the opened menu");

			assertEquals(54, menu.bukkitInventory().getSize());
			// Page 1 of 2: region full (first + last cell both filled), next button present, home/prev absent
			// (still border fill — SimplePagedMenu always borders too, same shape as the YAML paged path).
			assertEquals(Material.STONE, menu.bukkitInventory().getItem(10).getType());
			assertEquals(Material.STONE, menu.bukkitInventory().getItem(43).getType());
			assertEquals(Material.PLAYER_HEAD, menu.bukkitInventory().getItem(53).getType(), "next button on page 1");
			assertEquals(Material.BLACK_STAINED_GLASS_PANE, menu.bukkitInventory().getItem(49).getType(),
			            "no Home button on page 1");
			assertEquals(Material.BLACK_STAINED_GLASS_PANE, menu.bukkitInventory().getItem(45).getType(),
			            "no prev button on page 1");

			// Click "next" (slot 53) -> adoptComponentsFrom swaps page 2's components into this SAME ChestMenu.
			ItemStack            nextButtonItem = menu.bukkitInventory().getItem(53);
			InventoryClickEvent  event          = mock(InventoryClickEvent.class);
			when(event.getRawSlot()).thenReturn(53);
			when(event.getClick()).thenReturn(ClickType.LEFT);
			when(event.getCurrentItem()).thenReturn(nextButtonItem);

			menu.dispatchClick(event, player);

			// Page 2 of 2: only the remainder entry renders (last region cell empty), no next button (border
			// fill), Home present ONLY now (F1's ask), prev present.
			assertEquals(Material.STONE, menu.bukkitInventory().getItem(10).getType(), "remainder entry on page 2");
			assertNull(menu.bukkitInventory().getItem(43), "page 2 must not fill the region's last cell");
			assertEquals(Material.BLACK_STAINED_GLASS_PANE, menu.bukkitInventory().getItem(53).getType(),
			            "no next button on page 2 (last page)");
			assertEquals(Material.PLAYER_HEAD, menu.bukkitInventory().getItem(49).getType(),
			            "Home button must render on page 2");
			assertEquals(Material.PLAYER_HEAD, menu.bukkitInventory().getItem(45).getType(),
			            "prev button must render on page 2");
		}
	}

}
