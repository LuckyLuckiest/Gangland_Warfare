package org.luckyraven.gangland.shop.admin.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.keystone.item.ItemRefresherRegistry;
import org.luckyraven.keystone.shop.BarterCategory;
import org.luckyraven.keystone.shop.message.ShopDisplayResolver;
import org.luckyraven.keystone.shop.valuation.CategorySellValuator;

import java.math.BigDecimal;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Panel editor for a single {@link BarterCategory}'s template items. Mirror of {@link SellCategoryItemsAdminView} —
 * same flow-session integration, same cursor-drop / shift-click add-item pattern, different target list.
 *
 * <p>WS4 G1b (§0d): not an item-holding slot — see {@link ShopAdminView}'s class doc / {@link
 * SellCategoryItemsAdminView}'s class doc for the identical rationale.
 */
@RequiredArgsConstructor
public final class BarterCategoryItemsAdminView implements Panel<ShopAdminFlowSession> {

	private static final int   ROWS       = 6;
	private static final int   SLOT_BACK  = 45;
	private static final int   SLOT_INFO  = 49;
	private static final int   SLOT_PRICE = 53;
	private static final int[] ITEM_SLOTS = {
			0, 1, 2, 3, 4, 5, 6, 7, 8,
			9, 10, 11, 12, 13, 14, 15, 16, 17,
			18, 19, 20, 21, 22, 23, 24, 25, 26,
			27, 28, 29, 30, 31, 32, 33, 34, 35
	};

	private static final SoundEffect SOUND_BACK = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                            "UI_BUTTON_CLICK", 0.6f, 1.0f);

	private final JavaPlugin            plugin;
	private final ItemRefresherRegistry refresherRegistry;
	private final ShopDisplayResolver   displayResolver;

	private final Map<Player, ActiveContext> active = new WeakHashMap<>();

	@Override
	public int rows(ShopAdminFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(ShopAdminFlowSession session) {
		String name = session.barterCategoryInEdit != null ? session.barterCategoryInEdit.getDisplayName() : "Barter";
		return "&8Barter: &f" + name;
	}

	@Override
	public void render(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder, ShopAdminFlowSession session) {
		if (session.barterCategoryInEdit == null) {
			flow.back();
			return;
		}

		active.put(flow.viewer(), new ActiveContext(flow, session.barterCategoryInEdit));

		renderChrome(flow, builder, session);
		renderItems(flow, builder, session);
	}

	/** Called unconditionally by {@link ShopAdminFlow}'s flow-wide {@code onEnd} (see {@link ShopAdminView}). */
	public void onFlowEnd(Player admin) {
		active.remove(admin);
	}

	// ── Listener bridge ──────────────────────────────────────────────────

	public void handleClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player admin)) return;
		ActiveContext ctx = active.get(admin);
		if (ctx == null || ctx.flow.currentMenu() == null) return;
		if (event.getInventory() != ctx.flow.currentMenu().bukkitInventory()) return;

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

		if (event.getClickedInventory() == ctx.flow.currentMenu().bukkitInventory()) {
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
		ctx.flow.rerender();
	}

	private boolean isItemSlot(int rawSlot) {
		for (int s : ITEM_SLOTS) if (s == rawSlot) return true;
		return false;
	}

	private void renderChrome(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder,
	                          ShopAdminFlowSession session) {
		// Rows 4-5 (slots 36-53) are chrome; rows 0-3 (ITEM_SLOTS) stay genuinely empty wherever there's no item —
		// see SellCategoryItemsAdminView's identical comment for why this is an explicit range loop, not a
		// whole-inventory .fill()/.border().
		ItemStack pane = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem();
		if (pane == null) pane = new ItemStack(Material.STONE);
		ItemBuilder filler = new ItemBuilder(pane).setDisplayName(" ");
		for (int slot = 36; slot < ROWS * 9; slot++) {
			if (slot == SLOT_BACK || slot == SLOT_INFO || slot == SLOT_PRICE) continue;
			builder.slot(slot, ItemComponent.of(filler));
		}

		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to barter categories")
		                                                  .setLore("&7Save & return.");
		builder.slot(SLOT_BACK, ItemComponent.of(back).onAnyClick(ctx -> {
			flow.back();
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_BACK.playSound(ctx.player()));
		}));

		BarterCategory category = session.barterCategoryInEdit;
		ItemBuilder    info     = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		info.setDisplayName("&b" + category.getDisplayName())
		    .setLore("&7ID: &f" + category.getId(),
		             "&7Base value: &6$" + NumberUtil.valueFormat(category.getBasePrice()), " ",
		             "&7Drop or shift-click items to add;", "&7originals stay in your inventory.",
		             "&aL-click &7edit per-item value  &cR-click &7remove");
		builder.slot(SLOT_INFO, ItemComponent.of(info));

		ItemBuilder price = new ItemBuilder(material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT));
		price.setDisplayName("&6Edit base value")
		     .setLore("&7Current: &6$" + NumberUtil.valueFormat(category.getBasePrice()),
		              "&7Click to open the value editor.");
		builder.slot(SLOT_PRICE, ItemComponent.of(price).onAnyClick(ctx -> openBasePriceEditor(flow, session)));
	}

	private void renderItems(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder,
	                         ShopAdminFlowSession session) {
		BarterCategory category = session.barterCategoryInEdit;
		int            capacity = ITEM_SLOTS.length;
		for (int i = 0; i < capacity; i++) {
			int slot = ITEM_SLOTS[i];
			if (i < category.getItems().size()) {
				final int   finalIndex = i;
				ItemBuilder display    = new ItemBuilder(category.getItems().get(i).clone());
				builder.slot(slot, ItemComponent.of(display)
				                                .onLeftClick(ctx -> openPerItemPriceEditor(flow, session, finalIndex))
				                                .onRightClick(ctx -> removeItem(flow, session, finalIndex)));
			}
		}
	}

	private void removeItem(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session, int index) {
		BarterCategory category = session.barterCategoryInEdit;
		if (category == null || index >= category.getItems().size()) return;
		category.getItems().remove(index);
		flow.rerender();
	}

	private void openPerItemPriceEditor(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session,
	                                    int index) {
		BarterCategory category = session.barterCategoryInEdit;
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

		ItemStack decorated = refresherRegistry.decorate(source, flow.viewer());
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
		flow.switchTo(ShopAdminFlowSession.PANEL_PRICE_EDITOR);
	}

	private void openBasePriceEditor(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session) {
		BarterCategory category = session.barterCategoryInEdit;
		if (category == null) return;

		ItemStack preview = category.getItems().isEmpty()
		                    ? new ItemStack(Material.GOLD_INGOT)
		                    : category.getItems().get(0).clone();

		session.priceEditItem        = preview;
		session.priceEditOriginal    = category.getBasePrice();
		session.priceEditStaged      = category.getBasePrice();
		session.priceEditMode        = 1;
		session.priceEditTitleSuffix = "Barter " + category.getId();
		session.priceEditCommit      = category::setBasePrice;
		flow.switchTo(ShopAdminFlowSession.PANEL_PRICE_EDITOR);
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private record ActiveContext(MenuFlow<ShopAdminFlowSession> flow, BarterCategory category) { }

}
