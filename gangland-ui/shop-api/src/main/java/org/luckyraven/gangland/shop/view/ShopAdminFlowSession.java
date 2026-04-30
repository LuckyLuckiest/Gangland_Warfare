package org.luckyraven.gangland.shop.view;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.inventory.flow.FlowSession;
import org.luckyraven.gangland.item.ItemRefresherRegistry;
import org.luckyraven.gangland.shop.*;
import org.luckyraven.gangland.shop.event.ShopEditedEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared mutable state for the shop-admin flow. Every admin panel ({@link ShopAdminView}, {@link PriceEditorView},
 * {@link SellCategoryItemsAdminView}, {@link BarterCategoryItemsAdminView}) reads/mutates fields on this object rather
 * than maintaining its own per-player session map — one inventory handle, one session, smooth transitions.
 *
 * <p>Working copies of {@code buyEntries}, {@code sellCategories}, {@code barterCategories} are deep-copied from the
 * original {@link ShopDefinition} at flow start; a {@link ShopEditedEvent} is fired on flow end with the rebuilt
 * definition.
 */
public final class ShopAdminFlowSession implements FlowSession {

	public static final String PANEL_ADMIN           = "admin";
	public static final String PANEL_PRICE_EDITOR    = "price_editor";
	public static final String PANEL_SELL_CATEGORY   = "sell_category";
	public static final String PANEL_BARTER_CATEGORY = "barter_category";

	public final ShopDefinition        original;
	public final ItemRefresherRegistry refresherRegistry;
	public final List<ShopItemEntry>   buyEntries;
	public final List<SellCategory>    sellCategories;
	public final List<BarterCategory>  barterCategories;

	// ── ShopAdminView tab / page state ───────────────────────────────────
	public EntryKind currentKind = EntryKind.BUY;
	public int       currentPage = 0;

	// ── Category-edit context (populated before switchTo SELL/BARTER category panels) ──
	@Nullable
	public SellCategory   sellCategoryInEdit;
	@Nullable
	public BarterCategory barterCategoryInEdit;

	// ── Price-editor context (populated before switchTo PANEL_PRICE_EDITOR) ──
	@Nullable
	public ItemStack            priceEditItem;
	@Nullable
	public BigDecimal           priceEditOriginal;
	@Nullable
	public BigDecimal           priceEditStaged;
	public int                  priceEditMode = 1;
	@Nullable
	public String               priceEditTitleSuffix;
	// Called on SAVE with the staged price; typically mutates buyEntries / category item tags.
	@Nullable
	public Consumer<BigDecimal> priceEditCommit;

	public ShopAdminFlowSession(ShopDefinition original, ItemRefresherRegistry refresherRegistry,
	                            List<ShopItemEntry> buyEntries, List<SellCategory> sellCategories,
	                            List<BarterCategory> barterCategories) {
		this.original          = original;
		this.refresherRegistry = refresherRegistry;
		this.buyEntries        = new ArrayList<>(buyEntries);
		this.sellCategories    = sellCategories;
		this.barterCategories  = barterCategories;
	}

	/**
	 * Rebuilds the definition with the current working-copy lists. Called on flow end and fed into
	 * {@link ShopEditedEvent}.
	 */
	public ShopDefinition buildNewDefinition() {
		List<ShopItemEntry> out = new ArrayList<>(buyEntries.size());
		for (int i = 0; i < buyEntries.size(); i++) {
			ShopItemEntry source = buyEntries.get(i);
			BigDecimal    price  = source.getPrice();
			ItemStack     raw    = refresherRegistry.refresh(source.getItem(), null);
			if (price == null) price = BigDecimal.valueOf(100);
			out.add(new ShopItemEntry(i, EntryKind.BUY, raw, price));
		}
		return new ShopDefinition(original.getKey(), original.getTitle(), original.getSize(), out,
		                          original.getSellEntries(), sellCategories, barterCategories);
	}

}
