package me.luckyraven.market.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.market.contract.MarketPriceContract;
import me.luckyraven.market.contract.MarketSnapshotRepositoryContract;
import me.luckyraven.market.registry.MarketItemState;
import me.luckyraven.market.snapshot.DailySnapshot;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detail view for a single market item. Header row shows item name + live price + 24h / 7d / 30d changes; the body is a
 * 5-row bar chart of the last 9 days of close prices rendered by {@link ChartRenderer}.
 */
@RequiredArgsConstructor
public final class MarketDetailView {

	private static final int INVENTORY_SIZE = 54;
	private static final int HEADER_ROW     = 0;
	private static final int CHART_START    = 1;
	private static final int CHART_ROWS     = 4;
	private static final int CHART_COLS     = 9;
	private static final int NAV_ROW        = 5;
	private static final int SLOT_BACK      = 45;
	private static final int SLOT_INFO      = 49;

	private final JavaPlugin                       plugin;
	private final MarketPriceContract              market;
	private final MarketSnapshotRepositoryContract snapshotRepository;

	public void open(Player player, String itemId) {
		MarketItemState state = market.find(itemId).orElse(null);
		if (state == null) {
			player.sendMessage("§cUnknown item: " + itemId);
			return;
		}

		InventoryHandler handler = new InventoryHandler(plugin, "§8Market — §f" + itemId, INVENTORY_SIZE, player);

		fillRow(handler, HEADER_ROW, XMaterial.GRAY_STAINED_GLASS_PANE);
		fillRow(handler, NAV_ROW, XMaterial.GRAY_STAINED_GLASS_PANE);

		renderHeader(handler, state);
		renderChart(handler, itemId);
		renderNavigation(handler, state);

		handler.open(player);
	}

	private void renderHeader(InventoryHandler handler, MarketItemState state) {
		double change24h = market.percentageChange(state.getItemId(), 1);
		double change7d  = market.percentageChange(state.getItemId(), 7);
		double change30d = market.percentageChange(state.getItemId(), 30);

		ItemBuilder header = new ItemBuilder(new ItemStack(Material.PAPER));
		header.setDisplayName("&6" + state.getItemId())
		      .setLore("&7Current: &e" + String.format("%.2f", state.effectivePrice()),
		               "&7Base: &8" + String.format("%.2f", state.getBasePrice()), " ",
		               "&7Δ 24h: &e" + String.format("%+.2f%%", change24h * 100D),
		               "&7Δ 7d:  &e" + String.format("%+.2f%%", change7d * 100D),
		               "&7Δ 30d: &e" + String.format("%+.2f%%", change30d * 100D), " ",
		               state.isOverridden() ? "&6[override]" : state.isFrozen() ? "&b[frozen]" : "&7[live]");
		handler.setItem(4, header, false, (p, inv, b) -> { });
	}

	private void renderChart(InventoryHandler handler, String itemId) {
		List<DailySnapshot> history = new ArrayList<>(snapshotRepository.history(itemId, CHART_COLS));
		// history() returns newest-first; the chart expects oldest → newest so reverse.
		Collections.reverse(history);
		ChartRenderer.draw(handler, CHART_START, CHART_ROWS, CHART_COLS, history);
	}

	private void renderNavigation(InventoryHandler handler, MarketItemState state) {
		ItemBuilder back = new ItemBuilder(paneOf(XMaterial.LIME_STAINED_GLASS_PANE)).setDisplayName("&a◄ Close")
		                                                                             .setLore("&7Close this view.");
		handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> p.closeInventory());

		ItemBuilder info = new ItemBuilder(new ItemStack(Material.BOOK));
		info.setDisplayName("&eLegend")
		    .setLore("&7Each column = 1 day.", "&aLime &7= up vs previous day",
		             "&cRed &7= down vs previous day", "&8Gray &7= unchanged", " ",
		             "&7Last updated: &f" + state.getLastUpdatedMillis());
		handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });
	}

	private void fillRow(InventoryHandler handler, int row, XMaterial pane) {
		for (int c = 0; c < 9; c++) {
			int slot = row * 9 + c;
			handler.setItem(slot, new ItemBuilder(paneOf(pane)).setDisplayName(" "), false, (p, inv, b) -> { });
		}
	}

	private ItemStack paneOf(XMaterial preferred) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
	}
}
