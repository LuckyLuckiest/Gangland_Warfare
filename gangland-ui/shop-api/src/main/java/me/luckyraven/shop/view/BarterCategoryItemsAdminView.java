package me.luckyraven.shop.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.shop.BarterCategory;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.valuation.CategorySellValuator;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.NumberUtil;
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

/**
 * Admin editor for a single {@link BarterCategory}. Mirror of {@link SellCategoryItemsAdminView}: drop items into the
 * upper grid to declare which materials the category accepts; right-click an item to set its per-item barter value
 * (stored under the shared {@link CategorySellValuator#SELL_PRICE_NBT_KEY} NBT tag so a single admin-set value applies
 * to both sell and barter flows).
 */
@RequiredArgsConstructor
public final class BarterCategoryItemsAdminView implements BeanLifecycle {

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
		List<Player> admins = new ArrayList<>(active.keySet());
		for (Player admin : admins) {
			Session session = active.remove(admin);
			if (session == null) continue;
			session.pendingSubview = true;
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
			}
		}
	}

	public void open(Player admin, BarterCategory category, Runnable onClose) {
		String           title   = "&8Barter: &f" + category.getDisplayName();
		InventoryHandler handler = new InventoryHandler(plugin, title, INVENTORY_SIZE, admin);

		Session session = new Session(category, handler, onClose);
		active.put(admin, session);

		renderChrome(session);
		renderItems(session);

		handler.open(admin);
	}

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

		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to barter categories")
		                                                  .setLore("&7Save & return.");
		session.handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> {
			SOUND_BACK.playSound(p);
			p.closeInventory();
		});

		ItemBuilder info = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		info.setDisplayName("&b" + session.category.getDisplayName())
		    .setLore("&7ID: &f" + session.category.getId(),
		             "&7Base value: &6$" + NumberUtil.valueFormat(session.category.getBasePrice()), " ",
		             "&7Drop items above to declare which", "&7materials this category accepts.",
		             "&7Right-click an item to set its barter value.");
		session.handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });

		ItemBuilder price = new ItemBuilder(material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT));
		price.setDisplayName("&6Edit base value")
		     .setLore("&7Current: &6$" + NumberUtil.valueFormat(session.category.getBasePrice()),
		              "&7Click to open the value editor.");
		session.handler.setItem(SLOT_PRICE, price, false, (p, inv, b) -> openPriceEditor(p, session));
	}

	private void renderItems(Session session) {
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
		                            "Barter " + session.category.getId(), session.category::setBasePrice, reopen);
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
		final BarterCategory   category;
		final InventoryHandler handler;
		final Runnable         onClose;
		boolean pendingSubview = false;

		Session(BarterCategory category, InventoryHandler handler, Runnable onClose) {
			this.category = category;
			this.handler  = handler;
			this.onClose  = onClose;
		}
	}

}
