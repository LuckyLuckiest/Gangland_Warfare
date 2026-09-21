package org.luckyraven.gangland.shop.admin.view;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.item.ItemRefresherRegistry;
import org.luckyraven.keystone.shop.BarterCategory;
import org.luckyraven.keystone.shop.SellCategory;
import org.luckyraven.keystone.shop.ShopDefinition;
import org.luckyraven.keystone.shop.event.ShopEditedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the shop-admin flow. Builds a fresh {@link MenuFlow} per-admin with the four admin panels
 * registered, opens at {@link ShopAdminFlowSession#PANEL_ADMIN}, and fires a {@link ShopEditedEvent} on flow end so
 * the shop registry can persist the edited definition.
 *
 * <p>WS4 G1b: rebuilt onto Keystone's {@code keystone-inventory} ({@code MenuFlow}/{@code ChestMenuBuilder}) —
 * relocated off {@code MultiPanelInventory}/inventory-api (G1a left it unchanged; this is the Oriel rewrite). The
 * old {@code MultiPanelInventory#onEnd} could be registered per-render inside a panel; {@code MenuFlow}'s
 * {@code onEnd} is set once at flow construction, so the three views that track per-admin state
 * ({@link ShopAdminView}, {@link SellCategoryItemsAdminView}, {@link BarterCategoryItemsAdminView} — each via a
 * raw-listener bridge, same pattern as {@code BarterView}/{@code BarterSessionListener}) get their cleanup called
 * unconditionally here (a no-op for whichever views this admin never actually entered).
 */
@RequiredArgsConstructor
public final class ShopAdminFlow {

	private final JavaPlugin                   plugin;
	private final InventoryService             inventoryService;
	private final ItemRefresherRegistry        refresherRegistry;
	private final ShopAdminView                adminPanel;
	private final PriceEditorView              priceEditorPanel;
	private final SellCategoryItemsAdminView   sellCategoryPanel;
	private final BarterCategoryItemsAdminView barterCategoryPanel;

	public void start(Player admin, ShopDefinition def) {
		List<SellCategory>   sellCopy   = deepCopySell(def.getSellCategories());
		List<BarterCategory> barterCopy = deepCopyBarter(def.getBarterCategories());

		ShopAdminFlowSession session = new ShopAdminFlowSession(def, refresherRegistry,
		                                                        new ArrayList<>(def.getBuyEntries()), sellCopy,
		                                                        barterCopy);

		MenuFlow<ShopAdminFlowSession> flow = MenuFlow.builder(inventoryService, plugin, admin, session)
				.panel(ShopAdminFlowSession.PANEL_ADMIN, adminPanel)
				.panel(ShopAdminFlowSession.PANEL_PRICE_EDITOR, priceEditorPanel)
				.panel(ShopAdminFlowSession.PANEL_SELL_CATEGORY, sellCategoryPanel)
				.panel(ShopAdminFlowSession.PANEL_BARTER_CATEGORY, barterCategoryPanel)
				// Persist-on-end: rebuild the definition from the working copies and fire ShopEditedEvent so the
				// shop registry (or whoever listens) can write the updated shop to disk. Fires regardless of
				// whether the flow ended via the close button, ESC, or flow.end() — and regardless of which
				// panel was open at the time. Also clears the three raw-listener-tracked views' per-admin state.
				.onEnd(s -> {
					Bukkit.getPluginManager().callEvent(new ShopEditedEvent(admin, s.buildNewDefinition()));
					adminPanel.onFlowEnd(admin);
					sellCategoryPanel.onFlowEnd(admin);
					barterCategoryPanel.onFlowEnd(admin);
				})
				.build();

		flow.openAt(ShopAdminFlowSession.PANEL_ADMIN);
	}

	private List<SellCategory> deepCopySell(List<SellCategory> source) {
		List<SellCategory> copy = new ArrayList<>(source.size());
		for (SellCategory c : source) {
			copy.add(new SellCategory(c.getId(), c.getDisplayName(), c.getBasePrice(), c.getItems()));
		}
		return copy;
	}

	private List<BarterCategory> deepCopyBarter(List<BarterCategory> source) {
		List<BarterCategory> copy = new ArrayList<>(source.size());
		for (BarterCategory c : source) {
			copy.add(new BarterCategory(c.getId(), c.getDisplayName(), c.getBasePrice(), c.getItems()));
		}
		return copy;
	}

}
