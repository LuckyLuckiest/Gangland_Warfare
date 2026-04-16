package me.luckyraven.market.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.market.contract.MarketPriceContract;
import me.luckyraven.market.registry.MarketItemState;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Paginated list of every tracked market item. Each cell shows the item's id, current price, and 24h change. Clicking a
 * cell opens {@link MarketDetailView} for that item.
 */
@RequiredArgsConstructor
public final class MarketOverviewView {

	private static final int  INVENTORY_SIZE = 54;
	private static final int  ITEMS_PER_PAGE = 45;
	private static final int  SLOT_PREV_PAGE = 45;
	private static final int  SLOT_INFO      = 49;
	private static final int  SLOT_NEXT_PAGE = 53;
	private static final char ARROW_UP       = '\u25B2';
	private static final char ARROW_DOWN     = '\u25BC';
	private static final char ARROW_FLAT     = '\u25CF';

	private final JavaPlugin          plugin;
	private final MarketPriceContract market;
	private final MarketDetailView    detailView;

	public void open(Player player) {
		open(player, 0);
	}

	public void open(Player player, int page) {
		List<MarketItemState> states = new ArrayList<>(market.allStates());
		states.sort(Comparator.comparing(MarketItemState::getItemId));

		int totalPages = Math.max(1, (int) Math.ceil(states.size() / (double) ITEMS_PER_PAGE));
		int clamped    = Math.max(0, Math.min(page, totalPages - 1));

		InventoryHandler handler = new InventoryHandler(plugin,
		                                                "&8Market &7(page " + (clamped + 1) + "/" + totalPages + ")",
		                                                INVENTORY_SIZE, player);

		int from = clamped * ITEMS_PER_PAGE;
		int to   = Math.min(from + ITEMS_PER_PAGE, states.size());
		for (int i = from; i < to; i++) {
			MarketItemState state = states.get(i);
			int             slot  = i - from;
			renderItemCell(handler, slot, state, player);
		}

		for (int i = to - from; i < ITEMS_PER_PAGE; i++) {
			handler.setItem(i, new ItemBuilder(pane(XMaterial.BLACK_STAINED_GLASS_PANE)).setDisplayName(" "), false,
			                (p, inv, b) -> { });
		}

		renderNavigation(handler, clamped, totalPages, states.size());

		handler.open(player);
	}

	private void renderItemCell(InventoryHandler handler, int slot, MarketItemState state, Player player) {
		double change24h = market.percentageChange(state.getItemId(), 1);
		char   arrow     = change24h > 0 ? ARROW_UP : change24h < 0 ? ARROW_DOWN : ARROW_FLAT;
		String colour    = change24h > 0 ? "&a" : change24h < 0 ? "&c" : "&7";

		Material    display = materialFromId(state.getItemId());
		ItemBuilder builder = new ItemBuilder(new ItemStack(display));
		builder.setDisplayName("&f" + state.getItemId());
		builder.setLore("&7Price: &e" + String.format("%.2f", state.effectivePrice()),
		                "&7Base: &8" + String.format("%.2f", state.getBasePrice()),
		                colour + arrow + " " + String.format("%+.2f%%", change24h * 100D),
		                state.isOverridden() ? "&6[override]" : state.isFrozen() ? "&b[frozen]" : " ", " ",
		                "&8Click to open detail view.");
		handler.setItem(slot, builder, false, (p, inv, b) -> detailView.open(player, state.getItemId()));
	}

	private void renderNavigation(InventoryHandler handler, int page, int totalPages, int totalItems) {
		ItemBuilder prev = new ItemBuilder(pane(XMaterial.LIME_STAINED_GLASS_PANE)).setDisplayName("&a◄ Previous page")
		                                                                           .setLore("&7Page " + (page + 1) +
		                                                                                    "/" + totalPages);
		handler.setItem(SLOT_PREV_PAGE, prev, false,
		                (p, inv, b) -> { if (page > 0) open(p, page - 1); });

		ItemBuilder info = new ItemBuilder(new ItemStack(Material.PAPER)).setDisplayName("&6Market")
		                                                                 .setLore("&7Items tracked: &e" + totalItems,
		                                                                          "&7Index: &e" + String.format("%.3f",
		                                                                                                        market.index()));
		handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });

		ItemBuilder next = new ItemBuilder(pane(XMaterial.LIME_STAINED_GLASS_PANE)).setDisplayName("&aNext page ►")
		                                                                           .setLore("&7Page " + (page + 1) +
		                                                                                    "/" + totalPages);
		handler.setItem(SLOT_NEXT_PAGE, next, false,
		                (p, inv, b) -> { if (page < totalPages - 1) open(p, page + 1); });

		for (int slot = ITEMS_PER_PAGE; slot < INVENTORY_SIZE; slot++) {
			if (slot == SLOT_PREV_PAGE || slot == SLOT_INFO || slot == SLOT_NEXT_PAGE) {
				continue;
			}
			handler.setItem(slot, new ItemBuilder(pane(XMaterial.GRAY_STAINED_GLASS_PANE)).setDisplayName(" "), false,
			                (p, inv, b) -> { });
		}
	}

	private ItemStack pane(XMaterial preferred) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
	}

	private Material materialFromId(String itemId) {
		// Strip kind prefix (e.g. "material:diamond" → "diamond") to try a Material lookup for the icon.
		int    colon        = itemId.indexOf(':');
		String materialName = colon >= 0 ? itemId.substring(colon + 1) : itemId;
		try {
			return Material.valueOf(materialName.toUpperCase());
		} catch (IllegalArgumentException e) {
			return Material.PAPER;
		}
	}
}
