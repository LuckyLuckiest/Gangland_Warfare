package me.luckyraven.shop.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.flow.MultiPanelInventory;
import me.luckyraven.inventory.flow.Panel;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.shop.BarterCategory;
import me.luckyraven.shop.EntryKind;
import me.luckyraven.shop.SellCategory;
import me.luckyraven.shop.ShopItemEntry;
import me.luckyraven.shop.config.ShopUiSettings;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.message.ShopMessageContract;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.*;

/**
 * Root admin panel of the shop-admin flow. Three tabs (BUY entries / SELL categories / BARTER categories) with
 * pagination; clicks navigate to {@link PriceEditorView}, {@link SellCategoryItemsAdminView} or
 * {@link BarterCategoryItemsAdminView} via {@link MultiPanelInventory#switchTo(String)}. Template drops + shift-click
 * from the admin's inventory route through the {@code ShopAdminListener} which dispatches to {@link #handleClick} on
 * the active viewer's flow host.
 */
@RequiredArgsConstructor
public final class ShopAdminView implements Panel<ShopAdminFlowSession> {

	private static final BigDecimal DEFAULT_NEW_ENTRY_PRICE = BigDecimal.valueOf(100);

	private static final int   INVENTORY_SIZE   = 54;
	private static final int[] INTERIOR_SLOTS   = {
			10, 11, 12, 13, 14, 15, 16,
			19, 20, 21, 22, 23, 24, 25,
			28, 29, 30, 31, 32, 33, 34,
			37, 38, 39, 40, 41, 42, 43
	};
	private static final int   ENTRIES_PER_PAGE = INTERIOR_SLOTS.length;

	private static final int SLOT_TAB_BUY      = 45;
	private static final int SLOT_ADD_CATEGORY = 46;
	private static final int SLOT_TAB_BARTER   = 47;
	private static final int SLOT_PREV         = 48;
	private static final int SLOT_PAGE_INFO    = 49;
	private static final int SLOT_NEXT         = 50;
	private static final int SLOT_TAB_SELL     = 53;

	private static final SoundConfiguration SOUND_PAGE = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                            "UI_BUTTON_CLICK", 0.6f, 1.2f);
	private static final SoundConfiguration SOUND_TAB  = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                            "UI_BUTTON_CLICK", 0.6f, 1.5f);

	private final JavaPlugin            plugin;
	private final ItemRefresherRegistry refresherRegistry;
	private final ShopMessageContract   messages;
	private final ShopUiSettings        uiSettings;
	private final ShopDisplayResolver   displayResolver;

	private final Map<Player, ActiveContext> active = new WeakHashMap<>();

	@Override
	public int size(ShopAdminFlowSession session) {
		return INVENTORY_SIZE;
	}

	@Override
	public String title(ShopAdminFlowSession session) {
		return "&8Admin: &f" + session.original.getTitle();
	}

	@Override
	public void render(MultiPanelInventory<ShopAdminFlowSession> host, InventoryHandler handler, Player viewer,
	                   ShopAdminFlowSession session) {
		active.put(viewer, new ActiveContext(host, handler));
		host.onEnd(s -> active.remove(viewer));

		for (int i = 0; i < INVENTORY_SIZE; i++) handler.getInventory().setItem(i, null);

		switch (session.currentKind) {
			case BUY -> renderBuyList(host, handler, session);
			case SELL -> renderSellList(host, handler, session);
			case BARTER -> renderBarterList(host, handler, session);
		}

		renderTabs(host, handler, session);
		renderNavigation(host, handler, session);

		InventoryUtil.createBoarder(handler,
		                            new Fill(uiSettings.getInventoryFillName(), uiSettings.getInventoryFillItem()));
	}

	// ── Listener bridge (cursor-drop + shift-click add) ─────────────────

	public void handleClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player admin)) return;
		ActiveContext ctx = active.get(admin);
		if (ctx == null) return;
		if (event.getInventory() != ctx.handler.getInventory()) return;

		ShopAdminFlowSession session = ctx.host.session();
		if (session.currentKind != EntryKind.BUY) return;

		Inventory top    = event.getView().getTopInventory();
		Inventory bottom = event.getView().getBottomInventory();
		ClickType click  = event.getClick();

		if ((click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) &&
		    event.getClickedInventory() == bottom) {
			ItemStack src = event.getCurrentItem();
			if (src == null || src.getType().isAir()) return;

			event.setCancelled(true);
			appendEntryAndNavigate(ctx, session, src);
			return;
		}

		if (event.getClickedInventory() == top) {
			ItemStack cursor = event.getCursor();
			if (cursor == null || cursor.getType().isAir()) return;

			event.setCancelled(true);

			int slot = event.getSlot();
			if (!isInterior(slot)) return;

			appendEntryAndNavigate(ctx, session, cursor);
		}
	}

	private void appendEntryAndNavigate(ActiveContext ctx, ShopAdminFlowSession session, ItemStack source) {
		ItemStack refreshed = refresherRegistry.refresh(source, null);
		int       newIndex  = session.buyEntries.size();
		ShopItemEntry entry = new ShopItemEntry(newIndex, EntryKind.BUY, refreshed.clone(),
		                                        DEFAULT_NEW_ENTRY_PRICE);

		session.buyEntries.add(entry);
		session.currentPage = newIndex / ENTRIES_PER_PAGE;

		ctx.host.rerender();
		ctx.host.viewer().sendMessage(
				messages.shopAdminEntryAdded(displayResolver.cleanDisplayName(refreshed), newIndex,
				                             session.currentPage + 1));
	}

	private boolean isInterior(int slot) {
		for (int s : INTERIOR_SLOTS) if (s == slot) return true;
		return false;
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void renderBuyList(MultiPanelInventory<ShopAdminFlowSession> host, InventoryHandler handler,
	                           ShopAdminFlowSession session) {
		int base = session.currentPage * ENTRIES_PER_PAGE;
		for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
			int entryIndex = base + i;
			int slot       = INTERIOR_SLOTS[i];
			if (entryIndex >= session.buyEntries.size()) continue;

			ShopItemEntry entry      = session.buyEntries.get(entryIndex);
			final int     finalIndex = entryIndex;
			handler.setItem(slot, buildBuyEntryDisplay(entry, entryIndex), false,
			                (p, inv, b) -> onBuyLeftClick(host, session, finalIndex),
			                (p, inv, b) -> onBuyRightClick(host, session, finalIndex));
		}
	}

	private void renderSellList(MultiPanelInventory<ShopAdminFlowSession> host, InventoryHandler handler,
	                            ShopAdminFlowSession session) {
		int base = session.currentPage * ENTRIES_PER_PAGE;
		for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
			int categoryIndex = base + i;
			int slot          = INTERIOR_SLOTS[i];
			if (categoryIndex >= session.sellCategories.size()) continue;

			SellCategory category   = session.sellCategories.get(categoryIndex);
			final int    finalIndex = categoryIndex;
			handler.setItem(slot, buildCategoryDisplay(category), false,
			                (p, inv, b) -> onSellCategoryLeftClick(host, session, finalIndex),
			                (p, inv, b) -> onSellCategoryRightClick(host, session, finalIndex));
		}
	}

	private void renderBarterList(MultiPanelInventory<ShopAdminFlowSession> host, InventoryHandler handler,
	                              ShopAdminFlowSession session) {
		int base = session.currentPage * ENTRIES_PER_PAGE;
		for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
			int categoryIndex = base + i;
			int slot          = INTERIOR_SLOTS[i];
			if (categoryIndex >= session.barterCategories.size()) continue;

			BarterCategory category   = session.barterCategories.get(categoryIndex);
			final int      finalIndex = categoryIndex;
			handler.setItem(slot, buildBarterCategoryDisplay(category), false,
			                (p, inv, b) -> onBarterCategoryLeftClick(host, session, finalIndex),
			                (p, inv, b) -> onBarterCategoryRightClick(host, session, finalIndex));
		}
	}

	private void renderTabs(MultiPanelInventory<ShopAdminFlowSession> host, InventoryHandler handler,
	                        ShopAdminFlowSession session) {
		boolean     buyActive = session.currentKind == EntryKind.BUY;
		ItemBuilder buyTab    = new ItemBuilder(material(XMaterial.EMERALD, Material.EMERALD));
		buyTab.setDisplayName(buyActive ? "&a&l» BUY entries «" : "&aBUY entries")
		      .setLore("&7Items this trader sells to players.", buyActive ? "&e(active)" : "&8(click to switch)");
		handler.setItem(SLOT_TAB_BUY, buyTab, false, (p, inv, b) -> switchTab(host, session, EntryKind.BUY));

		boolean     sellActive = session.currentKind == EntryKind.SELL;
		ItemBuilder sellTab    = new ItemBuilder(material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT));
		sellTab.setDisplayName(sellActive ? "&6&l» SELL categories «" : "&6SELL categories")
		       .setLore("&7Item groups this trader buys from players.",
		                sellActive ? "&e(active)" : "&8(click to switch)");
		handler.setItem(SLOT_TAB_SELL, sellTab, false, (p, inv, b) -> switchTab(host, session, EntryKind.SELL));

		boolean     barterActive = session.currentKind == EntryKind.BARTER;
		ItemBuilder barterTab    = new ItemBuilder(material(XMaterial.DIAMOND, Material.DIAMOND));
		barterTab.setDisplayName(barterActive ? "&b&l» BARTER categories «" : "&bBARTER categories")
		         .setLore("&7Item groups players can offer as", "&7pure-swap payment for buy entries.",
		                  barterActive ? "&e(active)" : "&8(click to switch)");
		handler.setItem(SLOT_TAB_BARTER, barterTab, false, (p, inv, b) -> switchTab(host, session, EntryKind.BARTER));

		if (session.currentKind == EntryKind.SELL) {
			ItemBuilder add = new ItemBuilder(material(XMaterial.LIME_CONCRETE, Material.GREEN_WOOL));
			add.setDisplayName("&a+ Add category").setLore("&7Click to create a new sell category.");
			handler.setItem(SLOT_ADD_CATEGORY, add, false, (p, inv, b) -> openAddSellCategoryAnvil(host, session));
		} else if (session.currentKind == EntryKind.BARTER) {
			ItemBuilder add = new ItemBuilder(material(XMaterial.LIME_CONCRETE, Material.GREEN_WOOL));
			add.setDisplayName("&a+ Add category").setLore("&7Click to create a new barter category.");
			handler.setItem(SLOT_ADD_CATEGORY, add, false, (p, inv, b) -> openAddBarterCategoryAnvil(host, session));
		}
	}

	private void renderNavigation(MultiPanelInventory<ShopAdminFlowSession> host, InventoryHandler handler,
	                              ShopAdminFlowSession session) {
		int totalPages = totalPages(session);
		int current    = session.currentPage;

		if (current > 0) {
			ItemBuilder prev = new ItemBuilder(Material.ARROW).setDisplayName("&e◄ Previous page")
			                                                  .setLore("&7Go to page " + current + ".");
			handler.setItem(SLOT_PREV, prev, false, (p, inv, b) -> changePage(host, session, current - 1));
		}

		int entryCount = entryCount(session);

		ItemBuilder info = new ItemBuilder(Material.PAPER);
		info.setDisplayName("&bPage &f" + (current + 1) + "&7/&f" + totalPages)
		    .setLore("&7" + entryCount + " item(s) total.", "&7" + ENTRIES_PER_PAGE + " slots per page.");
		handler.setItem(SLOT_PAGE_INFO, info, false, (p, inv, b) -> { });

		boolean hasNext = current < totalPages - 1 || isLastPageFull(session);
		if (hasNext) {
			ItemBuilder next = new ItemBuilder(Material.ARROW);
			next.setDisplayName("&eNext page ►").setLore("&7Go to page " + (current + 2) + ".");
			handler.setItem(SLOT_NEXT, next, false, (p, inv, b) -> changePage(host, session, current + 1));
		}
	}

	private int entryCount(ShopAdminFlowSession session) {
		return switch (session.currentKind) {
			case BUY -> session.buyEntries.size();
			case SELL -> session.sellCategories.size();
			case BARTER -> session.barterCategories.size();
		};
	}

	private int totalPages(ShopAdminFlowSession session) {
		int count = entryCount(session);
		if (count == 0) return 1;
		return (int) Math.ceil(count / (double) ENTRIES_PER_PAGE);
	}

	private boolean isLastPageFull(ShopAdminFlowSession session) {
		int count = entryCount(session);
		return count > 0 && count % ENTRIES_PER_PAGE == 0;
	}

	private void changePage(MultiPanelInventory<ShopAdminFlowSession> host, ShopAdminFlowSession session, int newPage) {
		int maxPage = totalPages(session) - 1 + (isLastPageFull(session) ? 1 : 0);
		int clamped = Math.clamp(newPage, 0, maxPage);
		if (clamped == session.currentPage) return;
		session.currentPage = clamped;
		host.rerender();
		Bukkit.getScheduler().runTask(plugin, () -> SOUND_PAGE.playSound(host.viewer()));
	}

	private void switchTab(MultiPanelInventory<ShopAdminFlowSession> host, ShopAdminFlowSession session,
	                       EntryKind kind) {
		if (session.currentKind == kind) return;
		session.currentKind = kind;
		session.currentPage = 0;
		host.rerender();
		Bukkit.getScheduler().runTask(plugin, () -> SOUND_TAB.playSound(host.viewer()));
	}

	// ── Click handlers ───────────────────────────────────────────────────

	private void onBuyLeftClick(MultiPanelInventory<ShopAdminFlowSession> host, ShopAdminFlowSession session,
	                            int entryIndex) {
		if (session.currentKind != EntryKind.BUY) return;
		if (entryIndex < 0 || entryIndex >= session.buyEntries.size()) return;

		ShopItemEntry entry    = session.buyEntries.get(entryIndex);
		BigDecimal    current  = entry.getPrice();
		BigDecimal    original = current != null ? current : DEFAULT_NEW_ENTRY_PRICE;

		session.priceEditItem        = entry.getItem();
		session.priceEditOriginal    = original;
		session.priceEditStaged      = original;
		session.priceEditMode        = 1;
		session.priceEditTitleSuffix = "Slot " + entryIndex;
		session.priceEditCommit      = value -> {
			if (entryIndex >= session.buyEntries.size()) return;
			ShopItemEntry existing = session.buyEntries.get(entryIndex);
			session.buyEntries.set(entryIndex,
			                       new ShopItemEntry(entryIndex, existing.getKind(), existing.getItem(), value));
		};
		host.switchTo(ShopAdminFlowSession.PANEL_PRICE_EDITOR);
	}

	private void onBuyRightClick(MultiPanelInventory<ShopAdminFlowSession> host, ShopAdminFlowSession session,
	                             int entryIndex) {
		if (session.currentKind != EntryKind.BUY) return;
		if (entryIndex < 0 || entryIndex >= session.buyEntries.size()) return;

		session.buyEntries.remove(entryIndex);
		int maxPage = totalPages(session) - 1;
		if (session.currentPage > maxPage) session.currentPage = maxPage;

		host.rerender();
		host.viewer().sendMessage(messages.shopAdminEntryRemoved(entryIndex));
	}

	private void onSellCategoryLeftClick(MultiPanelInventory<ShopAdminFlowSession> host,
	                                     ShopAdminFlowSession session, int categoryIndex) {
		if (session.currentKind != EntryKind.SELL) return;
		if (categoryIndex < 0 || categoryIndex >= session.sellCategories.size()) return;

		session.sellCategoryInEdit = session.sellCategories.get(categoryIndex);
		host.switchTo(ShopAdminFlowSession.PANEL_SELL_CATEGORY);
	}

	private void onSellCategoryRightClick(MultiPanelInventory<ShopAdminFlowSession> host,
	                                      ShopAdminFlowSession session, int categoryIndex) {
		if (session.currentKind != EntryKind.SELL) return;
		if (categoryIndex < 0 || categoryIndex >= session.sellCategories.size()) return;

		SellCategory removed = session.sellCategories.remove(categoryIndex);
		int          maxPage = totalPages(session) - 1;
		if (session.currentPage > maxPage) session.currentPage = Math.max(0, maxPage);

		host.rerender();
		host.viewer().sendMessage(messages.shopAdminCategoryRemoved(removed.getId()));
	}

	private void onBarterCategoryLeftClick(MultiPanelInventory<ShopAdminFlowSession> host,
	                                       ShopAdminFlowSession session, int categoryIndex) {
		if (session.currentKind != EntryKind.BARTER) return;
		if (categoryIndex < 0 || categoryIndex >= session.barterCategories.size()) return;

		session.barterCategoryInEdit = session.barterCategories.get(categoryIndex);
		host.switchTo(ShopAdminFlowSession.PANEL_BARTER_CATEGORY);
	}

	private void onBarterCategoryRightClick(MultiPanelInventory<ShopAdminFlowSession> host,
	                                        ShopAdminFlowSession session, int categoryIndex) {
		if (session.currentKind != EntryKind.BARTER) return;
		if (categoryIndex < 0 || categoryIndex >= session.barterCategories.size()) return;

		BarterCategory removed = session.barterCategories.remove(categoryIndex);
		int            maxPage = totalPages(session) - 1;
		if (session.currentPage > maxPage) session.currentPage = Math.max(0, maxPage);

		host.rerender();
		host.viewer().sendMessage(messages.shopAdminCategoryRemoved(removed.getId()));
	}

	// ── Anvil category creation ──────────────────────────────────────────

	private void openAddSellCategoryAnvil(MultiPanelInventory<ShopAdminFlowSession> host,
	                                      ShopAdminFlowSession session) {
		host.suspend();
		new AnvilGUI.Builder()
				.plugin(plugin)
				.title("New category id")
				.itemLeft(material(XMaterial.PAPER, Material.PAPER))
				.text("category_id")
				.onClick((slot, state) -> {
					if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
					Player admin = host.viewer();

					String raw = state.getText() == null ? "" : state.getText().trim();
					if (raw.isEmpty()) {
						admin.sendMessage(ChatUtil.color("&cCategory id cannot be empty."));
						return Collections.emptyList();
					}

					String id = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
					if (session.original.getSellCategoryById(id) != null || hasSellCategory(session, id)) {
						admin.sendMessage(ChatUtil.color("&cCategory '" + id + "' already exists."));
						return Collections.emptyList();
					}

					session.sellCategories.add(SellCategory.empty(id));
					session.currentPage = (session.sellCategories.size() - 1) / ENTRIES_PER_PAGE;
					admin.sendMessage(messages.shopAdminCategoryCreated(id));

					return List.of(AnvilGUI.ResponseAction.close());
				})
				.onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> {
					host.resume();
					host.switchTo(ShopAdminFlowSession.PANEL_ADMIN);
				}))
				.open(host.viewer());
	}

	private void openAddBarterCategoryAnvil(MultiPanelInventory<ShopAdminFlowSession> host,
	                                        ShopAdminFlowSession session) {
		host.suspend();
		new AnvilGUI.Builder()
				.plugin(plugin)
				.title("New barter category id")
				.itemLeft(material(XMaterial.PAPER, Material.PAPER))
				.text("category_id")
				.onClick((slot, state) -> {
					if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
					Player admin = host.viewer();

					String raw = state.getText() == null ? "" : state.getText().trim();
					if (raw.isEmpty()) {
						admin.sendMessage(ChatUtil.color("&cCategory id cannot be empty."));
						return Collections.emptyList();
					}

					String id = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
					if (session.original.getBarterCategoryById(id) != null || hasBarterCategory(session, id)) {
						admin.sendMessage(ChatUtil.color("&cBarter category '" + id + "' already exists."));
						return Collections.emptyList();
					}

					session.barterCategories.add(BarterCategory.empty(id));
					session.currentPage = (session.barterCategories.size() - 1) / ENTRIES_PER_PAGE;
					admin.sendMessage(messages.shopAdminCategoryCreated(id));

					return List.of(AnvilGUI.ResponseAction.close());
				})
				.onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> {
					host.resume();
					host.switchTo(ShopAdminFlowSession.PANEL_ADMIN);
				}))
				.open(host.viewer());
	}

	private boolean hasSellCategory(ShopAdminFlowSession session, String id) {
		for (SellCategory c : session.sellCategories) if (c.getId().equalsIgnoreCase(id)) return true;
		return false;
	}

	private boolean hasBarterCategory(ShopAdminFlowSession session, String id) {
		for (BarterCategory c : session.barterCategories) if (c.getId().equalsIgnoreCase(id)) return true;
		return false;
	}

	// ── Display ──────────────────────────────────────────────────────────

	private ItemBuilder buildBuyEntryDisplay(ShopItemEntry entry, int entryIndex) {
		ItemStack   copy    = entry.getItem().clone();
		ItemBuilder builder = new ItemBuilder(copy);
		builder.setDisplayName(displayResolver.cleanDisplayName(copy));

		List<String> lore = new java.util.ArrayList<>();
		lore.add("&7Index: &f" + entryIndex);
		if (entry.hasPrice()) lore.add("&7Price: &6$" + NumberUtil.valueFormat(entry.getPrice()));
		lore.add("&aL-click &7set price  &cR-click &7remove");
		builder.setLore(lore);
		return builder;
	}

	private ItemBuilder buildCategoryDisplay(SellCategory category) {
		ItemStack icon = category.getItems().isEmpty()
		                 ? material(XMaterial.BOOK, Material.BOOK)
		                 : category.getItems().getFirst().clone();
		ItemBuilder builder = new ItemBuilder(icon);
		builder.setDisplayName("&6" + category.getDisplayName());

		List<String> lore = new java.util.ArrayList<>();
		lore.add("&7ID: &f" + category.getId());
		lore.add("&7Base price: &6$" + NumberUtil.valueFormat(category.getBasePrice()));
		lore.add("&7Items: &f" + category.getItems().size());
		lore.add("&aL-click &7edit items + price  &cR-click &7remove");
		builder.setLore(lore);
		return builder;
	}

	private ItemBuilder buildBarterCategoryDisplay(BarterCategory category) {
		ItemStack icon = category.getItems().isEmpty()
		                 ? material(XMaterial.DIAMOND, Material.DIAMOND)
		                 : category.getItems().getFirst().clone();
		ItemBuilder builder = new ItemBuilder(icon);
		builder.setDisplayName("&b" + category.getDisplayName());

		List<String> lore = new java.util.ArrayList<>();
		lore.add("&7ID: &f" + category.getId());
		lore.add("&7Base value: &6$" + NumberUtil.valueFormat(category.getBasePrice()));
		lore.add("&7Items: &f" + category.getItems().size());
		lore.add("&aL-click &7edit items + value  &cR-click &7remove");
		builder.setLore(lore);
		return builder;
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private record ActiveContext(MultiPanelInventory<ShopAdminFlowSession> host, InventoryHandler handler) { }

}
