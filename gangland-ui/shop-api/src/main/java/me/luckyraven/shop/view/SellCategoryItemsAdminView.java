package me.luckyraven.shop.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.bean.BeanLifecycle;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@RequiredArgsConstructor
public final class SellCategoryItemsAdminView implements BeanLifecycle {

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

	@Override
	public void onShutdown() {
		// Plugin is stopping — return every template item currently staged in the category editor back to the admin
		// so nothing they dragged in is silently lost on disable. commitItems is deliberately not called: the admin's
		// session ended abnormally, so we don't persist the half-edited snapshot.
		List<Player> admins = new ArrayList<>(active.keySet());
		for (Player admin : admins) {
			Session session = active.remove(admin);
			if (session == null) continue;
			session.pendingSubview = true;  // keep the close listener from commiting the partial snapshot
			for (int slot : ITEM_SLOTS) {
				ItemStack stack = session.handler.getInventory().getItem(slot);
				if (stack == null || stack.getType() == Material.AIR) continue;
				Map<Integer, ItemStack> leftover = admin.getInventory().addItem(stack.clone());
				for (ItemStack drop : leftover.values()) {
					admin.getWorld().dropItemNaturally(admin.getLocation(), drop);
				}
				session.handler.getInventory().setItem(slot, null);
			}
			try {
				admin.closeInventory();
			} catch (Exception ignored) {
				// Server is tearing down; swallow any late close errors.
			}
		}
	}

	public void open(Player admin, SellCategory category, Runnable onClose) {
		String           title   = "&8Category: &f" + category.getDisplayName();
		InventoryHandler handler = new InventoryHandler(plugin, title, INVENTORY_SIZE, admin);

		Session session = new Session(category, handler, onClose);
		active.put(admin, session);

		renderChrome(session);
		renderItems(session);

		handler.open(admin);
	}

	/**
	 * Right-click handler invoked by {@code SellCategoryAdminListener}. If the admin right-clicks a non-air item slot
	 * with an empty cursor, cancel the vanilla click and open the per-item price editor for that slot.
	 *
	 * @return {@code true} if the event should be cancelled (admin clicked an editable slot)
	 */
	public boolean handleRightClick(Player admin, org.bukkit.inventory.Inventory inventory, int rawSlot,
	                                ItemStack current, ItemStack cursor) {
		Session session = active.get(admin);
		if (session == null) return false;
		if (session.handler.getInventory() != inventory) return false;
		if (rawSlot < 0 || rawSlot >= ITEM_SLOTS.length) return false;
		if (current == null || current.getType() == Material.AIR) return false;
		if (cursor != null && cursor.getType() != Material.AIR) return false;

		openPerItemPriceEditor(admin, session, rawSlot, current.clone());
		return true;
	}

	/**
	 * Close handler invoked by {@code SellCategoryAdminListener}. Commits the current template snapshot and runs the
	 * parent navigation callback. Skipped while a sub-view is pending (e.g. the price editor).
	 */
	public void handleClose(Player admin, org.bukkit.inventory.Inventory inventory) {
		Session session = active.get(admin);
		if (session == null) return;
		if (session.handler.getInventory() != inventory) return;
		if (session.pendingSubview) return;

		commitItems(session);
		active.remove(admin);

		if (session.onClose != null) {
			Bukkit.getScheduler().runTask(plugin, session.onClose);
		}
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
		             "&7Drop items in the upper area to define", "&7which materials this category covers.");
		session.handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });

		ItemBuilder price = new ItemBuilder(material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT));
		price.setDisplayName("&6Edit base price")
		     .setLore("&7Current: &6$" + NumberUtil.valueFormat(session.category.getBasePrice()),
		              "&7Click to open the price editor.");
		session.handler.setItem(SLOT_PRICE, price, false, (p, inv, b) -> openPriceEditor(p, session));
	}

	private void renderItems(Session session) {
		// Slots must be registered as draggable so InventoryClickHandler doesn't auto-cancel placement clicks.
		int capacity = ITEM_SLOTS.length;
		for (int i = 0; i < capacity; i++) {
			int slot = ITEM_SLOTS[i];
			ItemStack stack = i < session.category.getItems().size() ?
			                  session.category.getItems().get(i).clone() :
			                  null;
			session.handler.setItem(slot, stack, true);
		}
	}

	private void openPerItemPriceEditor(Player admin, Session session, int slot, ItemStack source) {
		session.pendingSubview = true;

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
			renderItems(session);
			session.handler.open(admin);
		};

		ItemStack decorated = refresherRegistry.decorate(source, admin);
		String    label     = displayResolver.cleanDisplayName(decorated);
		priceEditorView.openGeneric(admin, source, currentPrice, "Item " + label, value -> {
			// Write the new price onto the live stack in the slot so commitItems picks it up.
			ItemStack live = session.handler.getInventory().getItem(slot);
			if (live != null && live.getType() != Material.AIR) {
				ItemStack tagged = new ItemBuilder(live)
						.addTag(CategorySellValuator.SELL_PRICE_NBT_KEY, value.toPlainString()).build();
				session.handler.getInventory().setItem(slot, tagged);
			}
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

	private void commitItems(Session session) {
		List<ItemStack> snapshot = new ArrayList<>();
		for (int slot : ITEM_SLOTS) {
			ItemStack stack = session.handler.getInventory().getItem(slot);
			if (stack != null && stack.getType() != Material.AIR) {
				snapshot.add(refresherRegistry.decorate(stack, null));
			}
		}
		session.category.replaceItems(snapshot);
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
