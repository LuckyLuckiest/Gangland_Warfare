package org.luckyraven.gangland.shop.view;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.item.ItemRefresherRegistry;
import org.luckyraven.gangland.shop.BarterCategory;
import org.luckyraven.gangland.shop.SellCategory;
import org.luckyraven.gangland.shop.ShopDefinition;
import org.luckyraven.gangland.shop.event.ShopEditedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the shop-admin flow. Builds a fresh {@link MultiPanelInventory} per-admin with the four admin panels
 * registered, opens at {@link ShopAdminFlowSession#PANEL_ADMIN}, and fires a {@link ShopEditedEvent} on flow end so the
 * shop registry can persist the edited definition.
 */
@RequiredArgsConstructor
public final class ShopAdminFlow {

	private final JavaPlugin                   plugin;
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

		MultiPanelInventory<ShopAdminFlowSession> host = new MultiPanelInventory<>(plugin, admin, session);
		host.register(ShopAdminFlowSession.PANEL_ADMIN, adminPanel);
		host.register(ShopAdminFlowSession.PANEL_PRICE_EDITOR, priceEditorPanel);
		host.register(ShopAdminFlowSession.PANEL_SELL_CATEGORY, sellCategoryPanel);
		host.register(ShopAdminFlowSession.PANEL_BARTER_CATEGORY, barterCategoryPanel);

		// Persist-on-end: rebuild the definition from the working copies and fire ShopEditedEvent so the shop
		// registry (or whoever listens) can write the updated shop to disk. Fires regardless of whether the flow
		// ended via the close button, ESC, or host.end().
		host.onEnd(s -> Bukkit.getPluginManager().callEvent(new ShopEditedEvent(admin, s.buildNewDefinition())));

		host.openAt(ShopAdminFlowSession.PANEL_ADMIN);
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
