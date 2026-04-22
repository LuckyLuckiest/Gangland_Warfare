package me.luckyraven.shop.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.flow.MultiPanelInventory;
import me.luckyraven.inventory.flow.Panel;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.shop.SellCategory;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.valuation.CategorySellValuator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Panel editor for a single {@link SellCategory}'s template items. Lives inside the shop-admin flow; transitions back
 * to {@link ShopAdminView} via {@link MultiPanelInventory#back()}, and into {@link PriceEditorView} via
 * {@link MultiPanelInventory#switchTo(String)} with the edit context pre-populated on the shared flow session.
 *
 * <p>The cursor-drop / shift-click "add item" pattern still runs through a dedicated {@code SellCategoryAdminListener}
 * that dispatches to {@link #handleClick} — the panel looks up the active host/category via the per-player
 * {@code active} map populated in {@link #render}.
 */
@RequiredArgsConstructor
public final class SellCategoryItemsAdminView implements Panel<ShopAdminFlowSession> {

	private static final int   INVENTORY_SIZE = 54;
	private static final int   SLOT_BACK      = 45;
	private static final int   SLOT_INFO      = 49;
	private static final int   SLOT_PRICE     = 53;
	private static final int[] ITEM_SLOTS     = {
			0, 1, 2, 3, 4, 5, 6, 7, 8,
			9, 10, 11, 12, 13, 14, 15, 16, 17,
			18, 19, 20, 21, 22, 23, 24, 25, 26,
			27, 28, 29, 30, 31, 32, 33, 34, 35
	};

	private static final SoundConfiguration SOUND_BACK = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                            "UI_BUTTON_CLICK", 0.6f, 1.0f);

	private final JavaPlugin            plugin;
	private final ItemRefresherRegistry refresherRegistry;
	private final ShopDisplayResolver   displayResolver;

	private final Map<Player, ActiveContext> active = new WeakHashMap<>();

	@Override
	public int size(ShopAdminFlowSession session) {
		return INVENTORY_SIZE;
	}

	@Override
	public String title(ShopAdminFlowSession session) {
		String name = session.sellCategoryInEdit != null ? session.sellCategoryInEdit.getDisplayName() : "Category";
		return "&8Category: &f" + name;
	}

	@Override
	public void render(MultiPanelInventory<ShopAdminFlowSession> host, InventoryHandler handler, Player viewer,
	                   ShopAdminFlowSession session) {
		if (session.sellCategoryInEdit == null) {
			host.back();
			return;
		}

		active.put(viewer, new ActiveContext(host, handler, session.sellCategoryInEdit));
		host.onEnd(s -> active.remove(viewer));

		renderChrome(host, handler, session);
		renderItems(host, handler, session);
	}

	// ── Listener bridge ──────────────────────────────────────────────────

	public void handleClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player admin)) return;
		ActiveContext ctx = active.get(admin);
		if (ctx == null) return;
		if (event.getInventory() != ctx.handler.getInventory()) return;

		Inventory bottom = event.getView().getBottomInventory();
		ClickType click  = event.getClick();

		if ((click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) &&
		    event.getClickedInventory() == bottom) {
			ItemStack src = event.getCurrentItem();
			if (src == null || src.getType().isAir()) return;

			event.setCancelled(true);
			appendItem(ctx, src);
			return;
		}

		if (event.getClickedInventory() == ctx.handler.getInventory()) {
			ItemStack cursor = event.getCursor();
			if (cursor == null || cursor.getType().isAir()) return;

			int rawSlot = event.getRawSlot();
			if (!isItemSlot(rawSlot)) return;

			event.setCancelled(true);
			appendItem(ctx, cursor);
		}
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void appendItem(ActiveContext ctx, ItemStack source) {
		if (ctx.category.getItems().size() >= ITEM_SLOTS.length) return;

		ItemStack copy = refresherRegistry.refresh(source, null);
		if (copy == null || copy.getType().isAir()) copy = source.clone();
		else copy = copy.clone();

		ctx.category.getItems().add(copy);
		renderItemsDirect(ctx);
	}

	private boolean isItemSlot(int rawSlot) {
		for (int s : ITEM_SLOTS) if (s == rawSlot) return true;
		return false;
	}

	private void renderChrome(MultiPanelInventory<ShopAdminFlowSession> host, InventoryHandler handler,
	                          ShopAdminFlowSession session) {
		ItemStack pane = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem();
		if (pane == null) pane = new ItemStack(Material.STONE);
		ItemBuilder filler = new ItemBuilder(pane).setDisplayName(" ");
		for (int slot = 36; slot < INVENTORY_SIZE; slot++) handler.setItem(slot, filler, false, (p, inv, b) -> { });

		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to categories")
		                                                  .setLore("&7Save & return.");
		handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> {
			host.back();
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_BACK.playSound(p));
		});

		SellCategory category = session.sellCategoryInEdit;
		ItemBuilder  info     = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		info.setDisplayName("&b" + category.getDisplayName())
		    .setLore("&7ID: &f" + category.getId(),
		             "&7Base price: &6$" + NumberUtil.valueFormat(category.getBasePrice()), " ",
		             "&7Drop or shift-click items to add;", "&7originals stay in your inventory.",
		             "&aL-click &7edit per-item price  &cR-click &7remove");
		handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });

		ItemBuilder price = new ItemBuilder(material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT));
		price.setDisplayName("&6Edit base price")
		     .setLore("&7Current: &6$" + NumberUtil.valueFormat(category.getBasePrice()),
		              "&7Click to open the price editor.");
		handler.setItem(SLOT_PRICE, price, false, (p, inv, b) -> openBasePriceEditor(host, session));
	}

	private void renderItems(MultiPanelInventory<ShopAdminFlowSession> host, InventoryHandler handler,
	                         ShopAdminFlowSession session) {
		SellCategory category = session.sellCategoryInEdit;
		int          capacity = ITEM_SLOTS.length;
		for (int i = 0; i < capacity; i++) {
			int slot = ITEM_SLOTS[i];
			if (i < category.getItems().size()) {
				final int   finalIndex = i;
				ItemBuilder display    = new ItemBuilder(category.getItems().get(i).clone());
				handler.setItem(slot, display, false,
				                (p, inv, b) -> openPerItemPriceEditor(host, session, finalIndex),
				                (p, inv, b) -> removeItem(host, session, finalIndex));
			} else {
				handler.getInventory().setItem(slot, null);
			}
		}
	}

	/**
	 * Used by the listener-dispatched appendItem path — no need to touch chrome, just refresh the item grid.
	 */
	private void renderItemsDirect(ActiveContext ctx) {
		int capacity = ITEM_SLOTS.length;
		for (int i = 0; i < capacity; i++) {
			int slot = ITEM_SLOTS[i];
			if (i < ctx.category.getItems().size()) {
				final int   finalIndex = i;
				ItemBuilder display    = new ItemBuilder(ctx.category.getItems().get(i).clone());
				ctx.handler.setItem(slot, display, false,
				                    (p, inv, b) -> openPerItemPriceEditor(ctx.host, ctx.host.session(), finalIndex),
				                    (p, inv, b) -> removeItem(ctx.host, ctx.host.session(), finalIndex));
			} else {
				ctx.handler.getInventory().setItem(slot, null);
			}
		}
	}

	private void removeItem(MultiPanelInventory<ShopAdminFlowSession> host, ShopAdminFlowSession session, int index) {
		SellCategory category = session.sellCategoryInEdit;
		if (category == null || index >= category.getItems().size()) return;
		category.getItems().remove(index);
		ActiveContext ctx = active.get(host.viewer());
		if (ctx != null) renderItemsDirect(ctx);
	}

	private void openPerItemPriceEditor(MultiPanelInventory<ShopAdminFlowSession> host, ShopAdminFlowSession session,
	                                    int index) {
		SellCategory category = session.sellCategoryInEdit;
		if (category == null || index >= category.getItems().size()) return;

		ItemStack source = category.getItems().get(index).clone();

		ItemBuilder builder = new ItemBuilder(source);
		BigDecimal  currentPrice;
		if (builder.hasNBTTag(CategorySellValuator.SELL_PRICE_NBT_KEY)) {
			Object raw = builder.getTagData(CategorySellValuator.SELL_PRICE_NBT_KEY);
			if (raw instanceof Number n) {
				currentPrice = BigDecimal.valueOf(n.doubleValue());
			} else if (raw != null) {
				try { currentPrice = new BigDecimal(String.valueOf(raw).trim()); } catch (
						NumberFormatException ignored) { currentPrice = category.getBasePrice(); }
			} else {
				currentPrice = category.getBasePrice();
			}
		} else {
			currentPrice = category.getBasePrice();
		}

		ItemStack decorated = refresherRegistry.decorate(source, host.viewer());
		String    label     = displayResolver.cleanDisplayName(decorated);
		session.priceEditItem        = source;
		session.priceEditOriginal    = currentPrice;
		session.priceEditStaged      = currentPrice;
		session.priceEditMode        = 1;
		session.priceEditTitleSuffix = "Item " + label;
		session.priceEditCommit      = value -> {
			if (index >= category.getItems().size()) return;
			ItemStack existing = category.getItems().get(index);
			ItemStack tagged = new ItemBuilder(existing)
					.addTag(CategorySellValuator.SELL_PRICE_NBT_KEY, value.toPlainString()).build();
			category.getItems().set(index, tagged);
		};
		host.switchTo(ShopAdminFlowSession.PANEL_PRICE_EDITOR);
	}

	private void openBasePriceEditor(MultiPanelInventory<ShopAdminFlowSession> host, ShopAdminFlowSession session) {
		SellCategory category = session.sellCategoryInEdit;
		if (category == null) return;

		ItemStack preview = category.getItems().isEmpty()
		                    ? new ItemStack(Material.GOLD_INGOT)
		                    : category.getItems().getFirst().clone();

		session.priceEditItem        = preview;
		session.priceEditOriginal    = category.getBasePrice();
		session.priceEditStaged      = category.getBasePrice();
		session.priceEditMode        = 1;
		session.priceEditTitleSuffix = "Category " + category.getId();
		session.priceEditCommit      = category::setBasePrice;
		host.switchTo(ShopAdminFlowSession.PANEL_PRICE_EDITOR);
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private record ActiveContext(MultiPanelInventory<ShopAdminFlowSession> host, InventoryHandler handler,
	                             SellCategory category) { }

}
