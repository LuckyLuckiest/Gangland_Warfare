package org.luckyraven.gangland.shop.admin.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.BorderComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.keystone.item.ItemRefresherRegistry;
import org.luckyraven.keystone.shop.BarterCategory;
import org.luckyraven.keystone.shop.EntryKind;
import org.luckyraven.keystone.shop.SellCategory;
import org.luckyraven.keystone.shop.ShopItemEntry;
import org.luckyraven.gangland.shop.config.ShopUiSettings;
import org.luckyraven.keystone.shop.message.ShopDisplayResolver;
import org.luckyraven.keystone.shop.message.ShopMessageContract;

import java.math.BigDecimal;
import java.util.*;

/**
 * Root admin panel of the shop-admin flow. Three tabs (BUY entries / SELL categories / BARTER categories) with
 * pagination; clicks navigate to {@link PriceEditorView}, {@link SellCategoryItemsAdminView} or
 * {@link BarterCategoryItemsAdminView} via {@link MenuFlow#switchTo(String)}. Template drops + shift-click from the
 * admin's inventory route through the {@code ShopAdminListener} which dispatches to {@link #handleClick} on the
 * active viewer's flow.
 *
 * <p>WS4 G1b (§0d): the BUY-tab template-drop mechanic is <strong>not</strong> an item-holding slot — the OLD code
 * (and this rebuild, unchanged) reads the player's cursor/shift-clicked item and {@code event.setCancelled(true)}s
 * the click <em>before</em> cloning it into a new {@link ShopItemEntry}; the original item never leaves the
 * player's cursor or bottom inventory, so there is nothing for Keystone's item-return contract to hold or lose —
 * see {@link #handleClick} and the WS4-G1b-report.md slot-by-slot table. This keeps the raw-listener-bridge shape
 * {@code BarterView}/{@code BarterSessionListener} already use in production for the parts of a click Keystone's
 * generic {@code Panel}/{@code ItemComponent} routing doesn't cover on its own (a shift-click from the player's own
 * inventory).
 */
@RequiredArgsConstructor
public final class ShopAdminView implements Panel<ShopAdminFlowSession> {

	private static final BigDecimal DEFAULT_NEW_ENTRY_PRICE = BigDecimal.valueOf(100);

	private static final int   ROWS             = 6;
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

	private static final SoundEffect SOUND_PAGE = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                            "UI_BUTTON_CLICK", 0.6f, 1.2f);
	private static final SoundEffect SOUND_TAB  = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                            "UI_BUTTON_CLICK", 0.6f, 1.5f);

	private final JavaPlugin            plugin;
	private final ItemRefresherRegistry refresherRegistry;
	private final ShopMessageContract   messages;
	private final ShopUiSettings        uiSettings;
	private final ShopDisplayResolver   displayResolver;

	private final Map<Player, MenuFlow<ShopAdminFlowSession>> active = new WeakHashMap<>();

	@Override
	public int rows(ShopAdminFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(ShopAdminFlowSession session) {
		return "&8Admin: &f" + session.original.getTitle();
	}

	@Override
	public void render(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder, ShopAdminFlowSession session) {
		active.put(flow.viewer(), flow);

		switch (session.currentKind) {
			case BUY -> renderBuyList(flow, builder, session);
			case SELL -> renderSellList(flow, builder, session);
			case BARTER -> renderBarterList(flow, builder, session);
		}

		renderTabs(flow, builder, session);
		renderNavigation(flow, builder, session);

		builder.border(BorderComponent.of(materialOf(uiSettings.getInventoryFillItem()))
		                              .name(uiSettings.getInventoryFillName()));
	}

	/**
	 * Called unconditionally by {@link ShopAdminFlow}'s flow-wide {@code onEnd} (the old per-render
	 * {@code MultiPanelInventory#onEnd} registration has no equivalent on {@link MenuFlow}, whose {@code onEnd} is
	 * fixed at flow construction).
	 */
	public void onFlowEnd(Player admin) {
		active.remove(admin);
	}

	// ── Listener bridge (cursor-drop + shift-click add) ─────────────────

	public void handleClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player admin)) return;
		MenuFlow<ShopAdminFlowSession> flow = active.get(admin);
		if (flow == null || flow.currentMenu() == null) return;
		if (event.getInventory() != flow.currentMenu().bukkitInventory()) return;

		ShopAdminFlowSession session = flow.state();
		if (session.currentKind != EntryKind.BUY) return;

		Inventory top    = event.getView().getTopInventory();
		Inventory bottom = event.getView().getBottomInventory();
		ClickType click  = event.getClick();

		if ((click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) &&
		    event.getClickedInventory() == bottom) {
			ItemStack src = event.getCurrentItem();
			if (src == null || src.getType().isAir()) return;

			event.setCancelled(true);
			appendEntryAndNavigate(flow, session, src);
			return;
		}

		if (event.getClickedInventory() == top) {
			ItemStack cursor = event.getCursor();
			if (cursor == null || cursor.getType().isAir()) return;

			event.setCancelled(true);

			int slot = event.getSlot();
			if (!isInterior(slot)) return;

			appendEntryAndNavigate(flow, session, cursor);
		}
	}

	private void appendEntryAndNavigate(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session,
	                                    ItemStack source) {
		ItemStack refreshed = refresherRegistry.refresh(source, null);
		int       newIndex  = session.buyEntries.size();
		ShopItemEntry entry = new ShopItemEntry(newIndex, EntryKind.BUY, refreshed.clone(),
		                                        DEFAULT_NEW_ENTRY_PRICE);

		session.buyEntries.add(entry);
		session.currentPage = newIndex / ENTRIES_PER_PAGE;

		flow.rerender();
		flow.viewer().sendMessage(
				messages.shopAdminEntryAdded(displayResolver.cleanDisplayName(refreshed), newIndex,
				                             session.currentPage + 1));
	}

	private boolean isInterior(int slot) {
		for (int s : INTERIOR_SLOTS) if (s == slot) return true;
		return false;
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void renderBuyList(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder,
	                           ShopAdminFlowSession session) {
		int base = session.currentPage * ENTRIES_PER_PAGE;
		for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
			int entryIndex = base + i;
			int slot       = INTERIOR_SLOTS[i];
			if (entryIndex >= session.buyEntries.size()) continue;

			ShopItemEntry entry      = session.buyEntries.get(entryIndex);
			final int     finalIndex = entryIndex;
			builder.slot(slot, ItemComponent.of(buildBuyEntryDisplay(entry, entryIndex))
			                                .onLeftClick(ctx -> onBuyLeftClick(flow, session, finalIndex))
			                                .onRightClick(ctx -> onBuyRightClick(flow, session, finalIndex)));
		}
	}

	private void renderSellList(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder,
	                            ShopAdminFlowSession session) {
		int base = session.currentPage * ENTRIES_PER_PAGE;
		for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
			int categoryIndex = base + i;
			int slot          = INTERIOR_SLOTS[i];
			if (categoryIndex >= session.sellCategories.size()) continue;

			SellCategory category   = session.sellCategories.get(categoryIndex);
			final int    finalIndex = categoryIndex;
			builder.slot(slot, ItemComponent.of(buildCategoryDisplay(category))
			                                .onLeftClick(ctx -> onSellCategoryLeftClick(flow, session, finalIndex))
			                                .onRightClick(ctx -> onSellCategoryRightClick(flow, session, finalIndex)));
		}
	}

	private void renderBarterList(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder,
	                              ShopAdminFlowSession session) {
		int base = session.currentPage * ENTRIES_PER_PAGE;
		for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
			int categoryIndex = base + i;
			int slot          = INTERIOR_SLOTS[i];
			if (categoryIndex >= session.barterCategories.size()) continue;

			BarterCategory category   = session.barterCategories.get(categoryIndex);
			final int      finalIndex = categoryIndex;
			builder.slot(slot, ItemComponent.of(buildBarterCategoryDisplay(category))
			                                .onLeftClick(ctx -> onBarterCategoryLeftClick(flow, session, finalIndex))
			                                .onRightClick(
					                                ctx -> onBarterCategoryRightClick(flow, session, finalIndex)));
		}
	}

	private void renderTabs(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder,
	                        ShopAdminFlowSession session) {
		boolean     buyActive = session.currentKind == EntryKind.BUY;
		ItemBuilder buyTab    = new ItemBuilder(material(XMaterial.EMERALD, Material.EMERALD));
		buyTab.setDisplayName(buyActive ? "&a&l» BUY entries «" : "&aBUY entries")
		      .setLore("&7Items this trader sells to players.", buyActive ? "&e(active)" : "&8(click to switch)");
		builder.slot(SLOT_TAB_BUY, ItemComponent.of(buyTab).onAnyClick(ctx -> switchTab(flow, session, EntryKind.BUY)));

		boolean     sellActive = session.currentKind == EntryKind.SELL;
		ItemBuilder sellTab    = new ItemBuilder(material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT));
		sellTab.setDisplayName(sellActive ? "&6&l» SELL categories «" : "&6SELL categories")
		       .setLore("&7Item groups this trader buys from players.",
		                sellActive ? "&e(active)" : "&8(click to switch)");
		builder.slot(SLOT_TAB_SELL,
		            ItemComponent.of(sellTab).onAnyClick(ctx -> switchTab(flow, session, EntryKind.SELL)));

		boolean     barterActive = session.currentKind == EntryKind.BARTER;
		ItemBuilder barterTab    = new ItemBuilder(material(XMaterial.DIAMOND, Material.DIAMOND));
		barterTab.setDisplayName(barterActive ? "&b&l» BARTER categories «" : "&bBARTER categories")
		         .setLore("&7Item groups players can offer as", "&7pure-swap payment for buy entries.",
		                  barterActive ? "&e(active)" : "&8(click to switch)");
		builder.slot(SLOT_TAB_BARTER,
		            ItemComponent.of(barterTab).onAnyClick(ctx -> switchTab(flow, session, EntryKind.BARTER)));

		if (session.currentKind == EntryKind.SELL) {
			ItemBuilder add = new ItemBuilder(material(XMaterial.LIME_CONCRETE, Material.GREEN_WOOL));
			add.setDisplayName("&a+ Add category").setLore("&7Click to create a new sell category.");
			builder.slot(SLOT_ADD_CATEGORY,
			            ItemComponent.of(add).onAnyClick(ctx -> openAddSellCategoryAnvil(flow, session)));
		} else if (session.currentKind == EntryKind.BARTER) {
			ItemBuilder add = new ItemBuilder(material(XMaterial.LIME_CONCRETE, Material.GREEN_WOOL));
			add.setDisplayName("&a+ Add category").setLore("&7Click to create a new barter category.");
			builder.slot(SLOT_ADD_CATEGORY,
			            ItemComponent.of(add).onAnyClick(ctx -> openAddBarterCategoryAnvil(flow, session)));
		}
	}

	private void renderNavigation(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder,
	                              ShopAdminFlowSession session) {
		int totalPages = totalPages(session);
		int current    = session.currentPage;

		if (current > 0) {
			ItemBuilder prev = new ItemBuilder(Material.ARROW).setDisplayName("&e◄ Previous page")
			                                                  .setLore("&7Go to page " + current + ".");
			builder.slot(SLOT_PREV, ItemComponent.of(prev).onAnyClick(ctx -> changePage(flow, session, current - 1)));
		}

		int entryCount = entryCount(session);

		ItemBuilder info = new ItemBuilder(Material.PAPER);
		info.setDisplayName("&bPage &f" + (current + 1) + "&7/&f" + totalPages)
		    .setLore("&7" + entryCount + " item(s) total.", "&7" + ENTRIES_PER_PAGE + " slots per page.");
		builder.slot(SLOT_PAGE_INFO, ItemComponent.of(info));

		boolean hasNext = current < totalPages - 1 || isLastPageFull(session);
		if (hasNext) {
			ItemBuilder next = new ItemBuilder(Material.ARROW);
			next.setDisplayName("&eNext page ►").setLore("&7Go to page " + (current + 2) + ".");
			builder.slot(SLOT_NEXT, ItemComponent.of(next).onAnyClick(ctx -> changePage(flow, session, current + 1)));
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

	private void changePage(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session, int newPage) {
		int maxPage = totalPages(session) - 1 + (isLastPageFull(session) ? 1 : 0);
		int clamped = Math.max(0, Math.min(newPage, maxPage));
		if (clamped == session.currentPage) return;
		session.currentPage = clamped;
		flow.rerender();
		Bukkit.getScheduler().runTask(plugin, () -> SOUND_PAGE.playSound(flow.viewer()));
	}

	private void switchTab(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session, EntryKind kind) {
		if (session.currentKind == kind) return;
		session.currentKind = kind;
		session.currentPage = 0;
		flow.rerender();
		Bukkit.getScheduler().runTask(plugin, () -> SOUND_TAB.playSound(flow.viewer()));
	}

	// ── Click handlers ───────────────────────────────────────────────────

	private void onBuyLeftClick(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session, int entryIndex) {
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
		flow.switchTo(ShopAdminFlowSession.PANEL_PRICE_EDITOR);
	}

	private void onBuyRightClick(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session, int entryIndex) {
		if (session.currentKind != EntryKind.BUY) return;
		if (entryIndex < 0 || entryIndex >= session.buyEntries.size()) return;

		session.buyEntries.remove(entryIndex);
		int maxPage = totalPages(session) - 1;
		if (session.currentPage > maxPage) session.currentPage = maxPage;

		flow.rerender();
		flow.viewer().sendMessage(messages.shopAdminEntryRemoved(entryIndex));
	}

	private void onSellCategoryLeftClick(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session,
	                                     int categoryIndex) {
		if (session.currentKind != EntryKind.SELL) return;
		if (categoryIndex < 0 || categoryIndex >= session.sellCategories.size()) return;

		session.sellCategoryInEdit = session.sellCategories.get(categoryIndex);
		flow.switchTo(ShopAdminFlowSession.PANEL_SELL_CATEGORY);
	}

	private void onSellCategoryRightClick(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session,
	                                      int categoryIndex) {
		if (session.currentKind != EntryKind.SELL) return;
		if (categoryIndex < 0 || categoryIndex >= session.sellCategories.size()) return;

		SellCategory removed = session.sellCategories.remove(categoryIndex);
		int          maxPage = totalPages(session) - 1;
		if (session.currentPage > maxPage) session.currentPage = Math.max(0, maxPage);

		flow.rerender();
		flow.viewer().sendMessage(messages.shopAdminCategoryRemoved(removed.getId()));
	}

	private void onBarterCategoryLeftClick(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session,
	                                       int categoryIndex) {
		if (session.currentKind != EntryKind.BARTER) return;
		if (categoryIndex < 0 || categoryIndex >= session.barterCategories.size()) return;

		session.barterCategoryInEdit = session.barterCategories.get(categoryIndex);
		flow.switchTo(ShopAdminFlowSession.PANEL_BARTER_CATEGORY);
	}

	private void onBarterCategoryRightClick(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session,
	                                        int categoryIndex) {
		if (session.currentKind != EntryKind.BARTER) return;
		if (categoryIndex < 0 || categoryIndex >= session.barterCategories.size()) return;

		BarterCategory removed = session.barterCategories.remove(categoryIndex);
		int            maxPage = totalPages(session) - 1;
		if (session.currentPage > maxPage) session.currentPage = Math.max(0, maxPage);

		flow.rerender();
		flow.viewer().sendMessage(messages.shopAdminCategoryRemoved(removed.getId()));
	}

	// ── Anvil category creation ──────────────────────────────────────────

	private void openAddSellCategoryAnvil(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session) {
		flow.suspend();
		new AnvilGUI.Builder()
				.plugin(plugin)
				.title("New category id")
				.itemLeft(material(XMaterial.PAPER, Material.PAPER))
				.text("category_id")
				.onClick((slot, state) -> {
					if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
					Player admin = flow.viewer();

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
					flow.resume();
					flow.switchTo(ShopAdminFlowSession.PANEL_ADMIN);
				}))
				.open(flow.viewer());
	}

	private void openAddBarterCategoryAnvil(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session) {
		flow.suspend();
		new AnvilGUI.Builder()
				.plugin(plugin)
				.title("New barter category id")
				.itemLeft(material(XMaterial.PAPER, Material.PAPER))
				.text("category_id")
				.onClick((slot, state) -> {
					if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
					Player admin = flow.viewer();

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
					flow.resume();
					flow.switchTo(ShopAdminFlowSession.PANEL_ADMIN);
				}))
				.open(flow.viewer());
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
		                 : category.getItems().get(0).clone();
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
		                 : category.getItems().get(0).clone();
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

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}

}
