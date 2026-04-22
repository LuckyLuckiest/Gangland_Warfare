package me.luckyraven.shop.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.inventory.InventoryHandler;
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
 * Admin editor for a single {@link SellCategory}'s template items. The editor never holds the admin's real items: any
 * cursor-drop or shift-click is intercepted, cloned into the category, and the admin's source is left untouched —
 * mirroring the buy-entry admin flow so admins never lose the items they're configuring with.
 */
@RequiredArgsConstructor
public final class SellCategoryItemsAdminView {

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
	private final PriceEditorView       priceEditorView;
	private final ItemRefresherRegistry refresherRegistry;
	private final ShopDisplayResolver   displayResolver;

	private final Map<Player, Session> active = new WeakHashMap<>();

	public void open(Player admin, SellCategory category, Runnable onClose) {
		String           title   = "&8Category: &f" + category.getDisplayName();
		InventoryHandler handler = new InventoryHandler(plugin, title, INVENTORY_SIZE, admin);

		Session session = new Session(category, handler, onClose);
		active.put(admin, session);

		renderChrome(session);
		renderItems(admin, session);

		handler.open(admin);
	}

	/**
	 * Event-level click handler invoked by {@code SellCategoryAdminListener}. Catches cursor-drops onto an editor slot
	 * and shift-clicks from the admin's inventory; in both cases the admin's source item is left in place and a clone
	 * is appended to the category's template list. Returns quickly when the click isn't one of the add-item patterns so
	 * the per-slot left/right handlers (edit price / remove) can run for clicks with an empty cursor.
	 */
	public void handleClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player admin)) return;
		Session session = active.get(admin);
		if (session == null) return;
		if (event.getInventory() != session.handler.getInventory()) return;

		Inventory bottom = event.getView().getBottomInventory();
		ClickType click  = event.getClick();

		if ((click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) &&
		    event.getClickedInventory() == bottom) {
			ItemStack src = event.getCurrentItem();
			if (src == null || src.getType().isAir()) return;

			event.setCancelled(true);
			appendItem(admin, session, src);
			return;
		}

		if (event.getClickedInventory() == session.handler.getInventory()) {
			ItemStack cursor = event.getCursor();
			if (cursor == null || cursor.getType().isAir()) return;

			int rawSlot = event.getRawSlot();
			if (!isItemSlot(rawSlot)) return;

			event.setCancelled(true);
			appendItem(admin, session, cursor);
		}
	}

	/**
	 * Close handler invoked by {@code SellCategoryAdminListener}. Click handlers mutate the category list directly, so
	 * close just runs the parent navigation callback.
	 */
	public void handleClose(Player admin, Inventory inventory) {
		Session session = active.get(admin);
		if (session == null) return;
		if (session.handler.getInventory() != inventory) return;
		if (session.pendingSubview) return;

		active.remove(admin);

		if (session.onClose != null) {
			Bukkit.getScheduler().runTask(plugin, session.onClose);
		}
	}

	private void appendItem(Player admin, Session session, ItemStack source) {
		if (session.category.getItems().size() >= ITEM_SLOTS.length) {
			return;
		}

		ItemStack copy = refresherRegistry.refresh(source, null);
		if (copy == null || copy.getType().isAir()) {
			copy = source.clone();
		} else {
			copy = copy.clone();
		}

		session.category.getItems().add(copy);
		renderItems(admin, session);
	}

	private boolean isItemSlot(int rawSlot) {
		for (int s : ITEM_SLOTS) {
			if (s == rawSlot) return true;
		}
		return false;
	}

	private void renderChrome(Session session) {
		ItemStack pane = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem();
		if (pane == null) {
			pane = new ItemStack(Material.STONE);
		}
		ItemBuilder filler = new ItemBuilder(pane).setDisplayName(" ");
		for (int slot = 36; slot < INVENTORY_SIZE; slot++) {
			session.handler.setItem(slot, filler, false, (p, inv, b) -> { });
		}

		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to categories")
		                                                  .setLore("&7Save & return.");
		session.handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> {
			SOUND_BACK.playSound(p);
			p.closeInventory();
		});

		ItemBuilder info = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		info.setDisplayName("&b" + session.category.getDisplayName())
		    .setLore("&7ID: &f" + session.category.getId(),
		             "&7Base price: &6$" + NumberUtil.valueFormat(session.category.getBasePrice()), " ",
		             "&7Drop or shift-click items to add;", "&7originals stay in your inventory.",
		             "&aL-click &7edit per-item price  &cR-click &7remove");
		session.handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });

		ItemBuilder price = new ItemBuilder(material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT));
		price.setDisplayName("&6Edit base price")
		     .setLore("&7Current: &6$" + NumberUtil.valueFormat(session.category.getBasePrice()),
		              "&7Click to open the price editor.");
		session.handler.setItem(SLOT_PRICE, price, false, (p, inv, b) -> openPriceEditor(p, session));
	}

	private void renderItems(Player admin, Session session) {
		int capacity = ITEM_SLOTS.length;
		for (int i = 0; i < capacity; i++) {
			int slot = ITEM_SLOTS[i];
			if (i < session.category.getItems().size()) {
				final int   finalIndex = i;
				ItemBuilder display    = new ItemBuilder(session.category.getItems().get(i).clone());
				session.handler.setItem(slot, display, false,
				                        (p, inv, b) -> openPerItemPriceEditor(admin, session, finalIndex),
				                        (p, inv, b) -> removeItem(admin, session, finalIndex));
			} else {
				session.handler.getInventory().setItem(slot, null);
			}
		}
	}

	private void removeItem(Player admin, Session session, int index) {
		if (index >= session.category.getItems().size()) return;
		session.category.getItems().remove(index);
		renderItems(admin, session);
	}

	private void openPerItemPriceEditor(Player admin, Session session, int index) {
		if (index >= session.category.getItems().size()) return;

		session.pendingSubview = true;
		ItemStack source = session.category.getItems().get(index).clone();

		ItemBuilder builder = new ItemBuilder(source);
		BigDecimal  currentPrice;
		if (builder.hasNBTTag(CategorySellValuator.SELL_PRICE_NBT_KEY)) {
			Object raw = builder.getTagData(CategorySellValuator.SELL_PRICE_NBT_KEY);
			if (raw instanceof Number n) {
				currentPrice = BigDecimal.valueOf(n.doubleValue());
			} else if (raw != null) {
				try {
					currentPrice = new BigDecimal(String.valueOf(raw).trim());
				} catch (NumberFormatException ignored) {
					currentPrice = session.category.getBasePrice();
				}
			} else {
				currentPrice = session.category.getBasePrice();
			}
		} else {
			currentPrice = session.category.getBasePrice();
		}

		Runnable reopen = () -> {
			session.pendingSubview = false;
			renderChrome(session);
			renderItems(admin, session);
			session.handler.open(admin);
		};

		ItemStack decorated = refresherRegistry.decorate(source, admin);
		String    label     = displayResolver.cleanDisplayName(decorated);
		priceEditorView.openGeneric(admin, source, currentPrice, "Item " + label, value -> {
			if (index >= session.category.getItems().size()) return;
			ItemStack existing = session.category.getItems().get(index);
			ItemStack tagged = new ItemBuilder(existing)
					.addTag(CategorySellValuator.SELL_PRICE_NBT_KEY, value.toPlainString()).build();
			session.category.getItems().set(index, tagged);
		}, reopen);
	}

	private void openPriceEditor(Player admin, Session session) {
		session.pendingSubview = true;

		ItemStack preview = session.category.getItems().isEmpty() ?
		                    new ItemStack(Material.GOLD_INGOT) :
		                    session.category.getItems().getFirst().clone();

		Runnable reopen = () -> {
			session.pendingSubview = false;
			renderChrome(session);
			session.handler.open(admin);
		};

		priceEditorView.openGeneric(admin, preview, session.category.getBasePrice(),
		                            "Category " + session.category.getId(), session.category::setBasePrice, reopen);
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	static final class Session {
		final SellCategory     category;
		final InventoryHandler handler;
		final Runnable         onClose;
		boolean pendingSubview = false;

		Session(SellCategory category, InventoryHandler handler, Runnable onClose) {
			this.category = category;
			this.handler  = handler;
			this.onClose  = onClose;
		}
	}

}
