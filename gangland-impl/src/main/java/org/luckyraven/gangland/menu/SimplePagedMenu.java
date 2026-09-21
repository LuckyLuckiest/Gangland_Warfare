package org.luckyraven.gangland.menu;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.chest.ChestMenu;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.click.ClickContext;
import org.luckyraven.keystone.inventory.component.BorderComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.page.PageConfig;
import org.luckyraven.keystone.inventory.page.PagedRegion;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.menu.part.ButtonTags;

import java.util.List;

/**
 * A no-static-items, no-per-entry-click paginated {@link ChestMenu} of plain {@link ItemStack}s — the shape
 * {@code DebugCommand}, {@code GangCommand} (member/ally lists) and {@code BountyAspect} each built via the old
 * {@code MultiInventoryCreation.dynamicMultiInventory(plugin, player, entries, title, false, 0, fill, buttonTags,
 * null)} call. WS2 G3: replaces that chained-Bukkit-inventory model with one fixed-size menu whose interior grid
 * renders via {@link PagedRegion}; next/prev/home rebuild the target page and swap it into the already-open menu
 * via {@link ChestMenu#adoptComponentsFrom} (no close/reopen flicker) — reusing {@link InventoryBuilder#headItem}
 * so the button textures/lore/click-sound match the YAML-driven paginated menus exactly (fix round 1, F1/M7).
 */
public final class SimplePagedMenu {

	private SimplePagedMenu() { }

	/** Builds and opens page 0 for {@code player}. */
	public static void open(InventoryService inventoryService, Player player, List<ItemStack> items, String title,
	                        Fill fill, ButtonTags buttonTags) {
		build(inventoryService, player, items, title, fill, buttonTags, 0).open(player);
	}

	private static ChestMenu build(InventoryService inventoryService, Player player, List<ItemStack> items,
	                               String title, Fill fill, ButtonTags buttonTags, int page) {
		List<PagedRegion.Entry> entries = items.stream().map(PagedRegion.Entry::new).toList();

		// Mirrors the old computeConfigForCreation(itemsCount, size=0, staticItemCount=0, staticItemsAllowed=false):
		// 7 columns, ceil(itemsCount / 7) + 2 rows, clamped to [3, 6].
		int rows = Math.max(3, Math.min(6, (int) Math.ceil(Math.max(entries.size(), 1) / 7.0) + 2));

		PageConfig pageConfig   = PageConfig.forSize(rows, 1, rows - 2, 1, 7, entries.size());
		int        resolvedPage = Math.min(Math.max(page, 0), Math.max(0, pageConfig.pageCount() - 1));

		String pagedTitle = title + String.format(" &8[&b%d&8/&3%d&8]&r", resolvedPage + 1, pageConfig.pageCount());

		ChestMenuBuilder builder = ChestMenu.builder(inventoryService).title(pagedTitle).rows(rows);
		PagedRegion.render(builder, pageConfig, entries, resolvedPage);

		Material fillMaterial = XMaterial.matchXMaterial(fill.material()).map(XMaterial::get)
		                                 .orElse(Material.BLACK_STAINED_GLASS_PANE);
		builder.border(BorderComponent.of(fillMaterial).name(fill.name()));

		int size = rows * 9;
		if (resolvedPage < pageConfig.pageCount() - 1) {
			String lore = String.format("&7(%d/%d)", resolvedPage + 2, pageConfig.pageCount());
			builder.slot(size - 1, ItemComponent.of(InventoryBuilder.headItem("&a->", buttonTags.nextPage(), lore))
			                                    .onLeftClick(ctx ->
					swap(ctx, build(inventoryService, player, items, title, fill, buttonTags, resolvedPage + 1))));
		}
		if (resolvedPage > 0) {
			// Home button at size-5 on every page after the first, matching the old MultiInventoryNavigation.
			builder.slot(size - 5, ItemComponent.of(InventoryBuilder.headItem("&cBack to " + title,
			                                                                 buttonTags.homePage()))
			                                    .onLeftClick(ctx ->
					swap(ctx, build(inventoryService, player, items, title, fill, buttonTags, 0))));
			String lore = String.format("&7(%d/%d)", resolvedPage, pageConfig.pageCount());
			builder.slot(size - 9, ItemComponent.of(InventoryBuilder.headItem("&c<-", buttonTags.previousPage(), lore))
			                                    .onLeftClick(ctx ->
					swap(ctx, build(inventoryService, player, items, title, fill, buttonTags, resolvedPage - 1))));
		}

		return builder.build();
	}

	private static void swap(ClickContext ctx, ChestMenu fresh) {
		if (ctx.menu() instanceof ChestMenu current) current.adoptComponentsFrom(fresh);
		InventoryBuilder.NAV_CLICK_SOUND.playSound(ctx.player());
	}

}
