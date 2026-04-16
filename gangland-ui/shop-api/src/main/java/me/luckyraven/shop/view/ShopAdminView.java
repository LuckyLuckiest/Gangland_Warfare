package me.luckyraven.shop.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.shop.*;
import me.luckyraven.shop.config.ShopUiSettings;
import me.luckyraven.shop.event.ShopEditedEvent;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.message.ShopMessageContract;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.util.utilities.NumberUtil;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

@RequiredArgsConstructor
public final class ShopAdminView {

	private static final double DEFAULT_NEW_ENTRY_PRICE = 100D;

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

	private final JavaPlugin                   plugin;
	private final PriceEditorView              priceEditorView;
	private final SellCategoryItemsAdminView   categoryItemsView;
	private final BarterCategoryItemsAdminView barterItemsView;
	private final ItemRefresherRegistry        refresherRegistry;
	private final ShopMessageContract          messages;
	private final ShopUiSettings               uiSettings;
	private final ShopDisplayResolver          displayResolver;

	private final Map<Player, Session> active = new WeakHashMap<>();

	public void open(Player admin, ShopDefinition def) {
		String           title   = "&8Admin: &f" + def.getTitle();
		InventoryHandler handler = new InventoryHandler(plugin, title, INVENTORY_SIZE, admin);

		List<ShopItemEntry>  buyEntries       = new ArrayList<>(def.getBuyEntries());
		List<SellCategory>   sellCategories   = deepCopy(def.getSellCategories());
		List<BarterCategory> barterCategories = deepCopyBarter(def.getBarterCategories());

		Session session = new Session(def, handler, buyEntries, sellCategories, barterCategories, refresherRegistry);
		active.put(admin, session);

		render(admin, session);
		handler.open(admin);
	}

	// ── External event hooks (called by ShopAdminListener) ──────────────

	public void handleClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player admin)) {
			return;
		}
		Session session = active.get(admin);
		if (session == null) {
			return;
		}
		if (event.getInventory() != session.handler.getInventory()) {
			return;
		}
		if (session.currentKind != EntryKind.BUY) {
			return;
		}

		Inventory top    = event.getView().getTopInventory();
		Inventory bottom = event.getView().getBottomInventory();
		ClickType click  = event.getClick();

		if ((click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) &&
		    event.getClickedInventory() == bottom) {
			ItemStack src = event.getCurrentItem();
			if (src == null || src.getType().isAir()) {
				return;
			}

			event.setCancelled(true);
			appendEntryAndNavigate(admin, session, src);
			return;
		}

		if (event.getClickedInventory() == top) {
			ItemStack cursor = event.getCursor();
			if (cursor == null || cursor.getType().isAir()) {
				return;
			}

			event.setCancelled(true);

			int slot = event.getSlot();
			if (!isInterior(slot)) {
				return;
			}

			appendEntryAndNavigate(admin, session, cursor);
		}
	}

	public void handleClose(Player admin, Inventory inventory) {
		Session session = active.get(admin);
		if (session == null) {
			return;
		}
		if (inventory != session.handler.getInventory()) {
			return;
		}
		if (session.pendingSubview) {
			return;
		}

		ShopDefinition updated = session.buildNewDefinition();
		Bukkit.getPluginManager().callEvent(new ShopEditedEvent(admin, updated));
		active.remove(admin);
	}

	private void appendEntryAndNavigate(Player admin, Session session, ItemStack source) {
		ItemStack refreshed = refresherRegistry.refresh(source, null);
		int       newIndex  = session.buyEntries.size();
		ShopItemEntry entry = new ShopItemEntry(newIndex, EntryKind.BUY, refreshed.clone(),
		                                        DEFAULT_NEW_ENTRY_PRICE);

		session.appendEntry(entry);
		session.currentPage = newIndex / ENTRIES_PER_PAGE;

		render(admin, session);
		admin.sendMessage(messages.shopAdminEntryAdded(displayResolver.cleanDisplayName(refreshed), newIndex,
		                                               session.currentPage + 1));
	}

	private boolean isInterior(int slot) {
		for (int s : INTERIOR_SLOTS) {
			if (s == slot) {
				return true;
			}
		}
		return false;
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void render(Player admin, Session session) {
		for (int i = 0; i < INVENTORY_SIZE; i++) {
			session.handler.getInventory().setItem(i, null);
		}

		switch (session.currentKind) {
			case BUY -> renderBuyList(admin, session);
			case SELL -> renderSellList(admin, session);
			case BARTER -> renderBarterList(admin, session);
		}

		renderTabs(admin, session);
		renderNavigation(admin, session);

		InventoryUtil.createBoarder(session.handler,
		                            new Fill(uiSettings.getInventoryFillName(), uiSettings.getInventoryFillItem()));
	}

	private void renderBuyList(Player admin, Session session) {
		int base = session.currentPage * ENTRIES_PER_PAGE;
		for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
			int entryIndex = base + i;
			int slot       = INTERIOR_SLOTS[i];

			if (entryIndex >= session.buyEntries.size()) {
				continue;
			}

			ShopItemEntry entry      = session.buyEntries.get(entryIndex);
			final int     finalIndex = entryIndex;
			session.handler.setItem(slot, buildBuyEntryDisplay(entry, entryIndex), false,
			                        (player, inv, b) -> onBuyLeftClick(admin, finalIndex),
			                        (player, inv, b) -> onBuyRightClick(admin, finalIndex));
		}
	}

	private void renderSellList(Player admin, Session session) {
		int base = session.currentPage * ENTRIES_PER_PAGE;
		for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
			int categoryIndex = base + i;
			int slot          = INTERIOR_SLOTS[i];

			if (categoryIndex >= session.sellCategories.size()) {
				continue;
			}

			SellCategory category   = session.sellCategories.get(categoryIndex);
			final int    finalIndex = categoryIndex;
			session.handler.setItem(slot, buildCategoryDisplay(category), false,
			                        (player, inv, b) -> onCategoryLeftClick(admin, finalIndex),
			                        (player, inv, b) -> onCategoryRightClick(admin, finalIndex));
		}
	}

	private void renderBarterList(Player admin, Session session) {
		int base = session.currentPage * ENTRIES_PER_PAGE;
		for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
			int categoryIndex = base + i;
			int slot          = INTERIOR_SLOTS[i];

			if (categoryIndex >= session.barterCategories.size()) {
				continue;
			}

			BarterCategory category   = session.barterCategories.get(categoryIndex);
			final int      finalIndex = categoryIndex;
			session.handler.setItem(slot, buildBarterCategoryDisplay(category), false,
			                        (player, inv, b) -> onBarterCategoryLeftClick(admin, finalIndex),
			                        (player, inv, b) -> onBarterCategoryRightClick(admin, finalIndex));
		}
	}

	private void renderTabs(Player admin, Session session) {
		boolean     buyActive = session.currentKind == EntryKind.BUY;
		ItemBuilder buyTab    = new ItemBuilder(material(XMaterial.EMERALD, Material.EMERALD));
		buyTab.setDisplayName(buyActive ? "&a&l» BUY entries «" : "&aBUY entries")
		      .setLore("&7Items this trader sells to players.", buyActive ? "&e(active)" : "&8(click to switch)");
		session.handler.setItem(SLOT_TAB_BUY, buyTab, false, (p, inv, b) -> switchTab(admin, session, EntryKind.BUY));

		boolean     sellActive = session.currentKind == EntryKind.SELL;
		ItemBuilder sellTab    = new ItemBuilder(material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT));
		sellTab.setDisplayName(sellActive ? "&6&l» SELL categories «" : "&6SELL categories")
		       .setLore("&7Item groups this trader buys from players.",
		                sellActive ? "&e(active)" : "&8(click to switch)");
		session.handler.setItem(SLOT_TAB_SELL, sellTab, false,
		                        (p, inv, b) -> switchTab(admin, session, EntryKind.SELL));

		boolean     barterActive = session.currentKind == EntryKind.BARTER;
		ItemBuilder barterTab    = new ItemBuilder(material(XMaterial.DIAMOND, Material.DIAMOND));
		barterTab.setDisplayName(barterActive ? "&b&l» BARTER categories «" : "&bBARTER categories")
		         .setLore("&7Item groups players can offer as", "&7pure-swap payment for buy entries.",
		                  barterActive ? "&e(active)" : "&8(click to switch)");
		session.handler.setItem(SLOT_TAB_BARTER, barterTab, false,
		                        (p, inv, b) -> switchTab(admin, session, EntryKind.BARTER));

		if (session.currentKind == EntryKind.SELL) {
			ItemBuilder add = new ItemBuilder(material(XMaterial.LIME_CONCRETE, Material.GREEN_WOOL));
			add.setDisplayName("&a+ Add category").setLore("&7Click to create a new sell category.");
			session.handler.setItem(SLOT_ADD_CATEGORY, add, false, (p, inv, b) -> openAddCategoryAnvil(admin, session));
		} else if (session.currentKind == EntryKind.BARTER) {
			ItemBuilder add = new ItemBuilder(material(XMaterial.LIME_CONCRETE, Material.GREEN_WOOL));
			add.setDisplayName("&a+ Add category").setLore("&7Click to create a new barter category.");
			session.handler.setItem(SLOT_ADD_CATEGORY, add, false,
			                        (p, inv, b) -> openAddBarterCategoryAnvil(admin, session));
		}
	}

	private void renderNavigation(Player admin, Session session) {
		int totalPages = session.totalPages();
		int current    = session.currentPage;

		if (current > 0) {
			ItemBuilder prev = new ItemBuilder(Material.ARROW).setDisplayName("&e◄ Previous page")
			                                                  .setLore("&7Go to page " + current + ".");
			session.handler.setItem(SLOT_PREV, prev, false, (p, inv, b) -> changePage(admin, session, current - 1));
		}

		int entryCount = switch (session.currentKind) {
			case BUY -> session.buyEntries.size();
			case SELL -> session.sellCategories.size();
			case BARTER -> session.barterCategories.size();
		};

		ItemBuilder info = new ItemBuilder(Material.PAPER);
		info.setDisplayName("&bPage &f" + (current + 1) + "&7/&f" + totalPages)
		    .setLore("&7" + entryCount + " item(s) total.", "&7" + ENTRIES_PER_PAGE + " slots per page.");
		session.handler.setItem(SLOT_PAGE_INFO, info, false, (p, inv, b) -> { });

		boolean hasNext = current < totalPages - 1 || isLastPageFull(session);
		if (hasNext) {
			ItemBuilder next = new ItemBuilder(Material.ARROW);
			next.setDisplayName("&eNext page ►").setLore("&7Go to page " + (current + 2) + ".");
			session.handler.setItem(SLOT_NEXT, next, false, (p, inv, b) -> changePage(admin, session, current + 1));
		}
	}

	private boolean isLastPageFull(Session session) {
		int count = switch (session.currentKind) {
			case BUY -> session.buyEntries.size();
			case SELL -> session.sellCategories.size();
			case BARTER -> session.barterCategories.size();
		};
		return count > 0 && count % ENTRIES_PER_PAGE == 0;
	}

	private void changePage(Player admin, Session session, int newPage) {
		int maxPage = session.totalPages() - 1 + (isLastPageFull(session) ? 1 : 0);
		int clamped = Math.clamp(newPage, 0, maxPage);
		if (clamped == session.currentPage) {
			return;
		}
		session.currentPage = clamped;
		SOUND_PAGE.playSound(admin);
		render(admin, session);
	}

	private void switchTab(Player admin, Session session, EntryKind kind) {
		if (session.currentKind == kind) {
			return;
		}
		session.currentKind = kind;
		session.currentPage = 0;
		SOUND_TAB.playSound(admin);
		render(admin, session);
	}

	// ── Click handlers ───────────────────────────────────────────────────

	private void onBuyLeftClick(Player admin, int entryIndex) {
		Session session = active.get(admin);
		if (session == null || session.currentKind != EntryKind.BUY) {
			return;
		}
		if (entryIndex < 0 || entryIndex >= session.buyEntries.size()) {
			return;
		}

		ShopItemEntry entry    = session.buyEntries.get(entryIndex);
		Double        current  = entry.getPrice();
		double        original = current != null ? current : DEFAULT_NEW_ENTRY_PRICE;

		session.pendingSubview = true;

		Runnable reopen = () -> {
			session.pendingSubview = false;
			render(admin, session);
			session.handler.open(admin);
		};

		priceEditorView.open(admin, session, entryIndex, entry.getItem(), original, reopen);
	}

	private void onBuyRightClick(Player admin, int entryIndex) {
		Session s = active.get(admin);
		if (s == null || s.currentKind != EntryKind.BUY) {
			return;
		}
		if (entryIndex < 0 || entryIndex >= s.buyEntries.size()) {
			return;
		}

		s.buyEntries.remove(entryIndex);
		int maxPage = s.totalPages() - 1;
		if (s.currentPage > maxPage) {
			s.currentPage = maxPage;
		}

		render(admin, s);
		admin.sendMessage(messages.shopAdminEntryRemoved(entryIndex));
	}

	private void onCategoryLeftClick(Player admin, int categoryIndex) {
		Session session = active.get(admin);
		if (session == null || session.currentKind != EntryKind.SELL) {
			return;
		}
		if (categoryIndex < 0 || categoryIndex >= session.sellCategories.size()) {
			return;
		}

		SellCategory category = session.sellCategories.get(categoryIndex);
		session.pendingSubview = true;

		Runnable reopen = () -> {
			session.pendingSubview = false;
			render(admin, session);
			session.handler.open(admin);
		};

		categoryItemsView.open(admin, category, reopen);
	}

	private void onCategoryRightClick(Player admin, int categoryIndex) {
		Session s = active.get(admin);
		if (s == null || s.currentKind != EntryKind.SELL) {
			return;
		}
		if (categoryIndex < 0 || categoryIndex >= s.sellCategories.size()) {
			return;
		}

		SellCategory removed = s.sellCategories.remove(categoryIndex);
		int          maxPage = s.totalPages() - 1;
		if (s.currentPage > maxPage) {
			s.currentPage = Math.max(0, maxPage);
		}

		render(admin, s);
		admin.sendMessage(messages.shopAdminCategoryRemoved(removed.getId()));
	}

	private void onBarterCategoryLeftClick(Player admin, int categoryIndex) {
		Session session = active.get(admin);
		if (session == null || session.currentKind != EntryKind.BARTER) {
			return;
		}
		if (categoryIndex < 0 || categoryIndex >= session.barterCategories.size()) {
			return;
		}

		BarterCategory category = session.barterCategories.get(categoryIndex);
		session.pendingSubview = true;

		Runnable reopen = () -> {
			session.pendingSubview = false;
			render(admin, session);
			session.handler.open(admin);
		};

		barterItemsView.open(admin, category, reopen);
	}

	private void onBarterCategoryRightClick(Player admin, int categoryIndex) {
		Session s = active.get(admin);
		if (s == null || s.currentKind != EntryKind.BARTER) {
			return;
		}
		if (categoryIndex < 0 || categoryIndex >= s.barterCategories.size()) {
			return;
		}

		BarterCategory removed = s.barterCategories.remove(categoryIndex);
		int            maxPage = s.totalPages() - 1;
		if (s.currentPage > maxPage) {
			s.currentPage = Math.max(0, maxPage);
		}

		render(admin, s);
		admin.sendMessage(messages.shopAdminCategoryRemoved(removed.getId()));
	}

	private void openAddBarterCategoryAnvil(Player admin, Session session) {
		session.pendingSubview = true;

		AnvilGUI.Builder builder = new AnvilGUI.Builder();
		builder.plugin(plugin)
		       .title("New barter category id")
		       .itemLeft(material(XMaterial.PAPER, Material.PAPER))
		       .text("category_id")
		       .onClick((slot, state) -> {
				   if (slot != AnvilGUI.Slot.OUTPUT) {
					   return Collections.emptyList();
				   }

				   String raw = state.getText() == null ? "" : state.getText().trim();
				   if (raw.isEmpty()) {
					   admin.sendMessage(ChatUtil.color("&cCategory id cannot be empty."));
					   return Collections.emptyList();
				   }

				   String id = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
				   if (session.original.getBarterCategoryById(id) != null || hasBarterCategoryInSession(session, id)) {
					   admin.sendMessage(ChatUtil.color("&cBarter category '" + id + "' already exists."));
					   return Collections.emptyList();
				   }

				   session.barterCategories.add(BarterCategory.empty(id));
				   session.currentPage = (session.barterCategories.size() - 1) / ENTRIES_PER_PAGE;
				   admin.sendMessage(messages.shopAdminCategoryCreated(id));

				   return List.of(AnvilGUI.ResponseAction.close());
			   })
		       .onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> {
				   session.pendingSubview = false;
				   render(admin, session);
				   session.handler.open(admin);
			   }))
		       .open(admin);
	}

	private boolean hasBarterCategoryInSession(Session session, String id) {
		for (BarterCategory category : session.barterCategories) {
			if (category.getId().equalsIgnoreCase(id)) {
				return true;
			}
		}
		return false;
	}

	private void openAddCategoryAnvil(Player admin, Session session) {
		session.pendingSubview = true;

		AnvilGUI.Builder builder = new AnvilGUI.Builder();
		builder.plugin(plugin)
		       .title("New category id")
		       .itemLeft(material(XMaterial.PAPER, Material.PAPER))
		       .text("category_id")
		       .onClick((slot, state) -> {
				   if (slot != AnvilGUI.Slot.OUTPUT) {
					   return Collections.emptyList();
				   }

				   String raw = state.getText() == null ? "" : state.getText().trim();
				   if (raw.isEmpty()) {
					   admin.sendMessage(ChatUtil.color("&cCategory id cannot be empty."));
					   return Collections.emptyList();
				   }

				   String id = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
				   if (session.original.getSellCategoryById(id) != null || hasCategoryInSession(session, id)) {
					   admin.sendMessage(ChatUtil.color("&cCategory '" + id + "' already exists."));
					   return Collections.emptyList();
				   }

				   session.sellCategories.add(SellCategory.empty(id));
				   session.currentPage = (session.sellCategories.size() - 1) / ENTRIES_PER_PAGE;
				   admin.sendMessage(messages.shopAdminCategoryCreated(id));

				   return List.of(AnvilGUI.ResponseAction.close());
			   })
		       .onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> {
				   session.pendingSubview = false;
				   render(admin, session);
				   session.handler.open(admin);
			   }))
		       .open(admin);
	}

	private boolean hasCategoryInSession(Session session, String id) {
		for (SellCategory category : session.sellCategories) {
			if (category.getId().equalsIgnoreCase(id)) {
				return true;
			}
		}
		return false;
	}

	// ── Display ──────────────────────────────────────────────────────────

	private ItemBuilder buildBuyEntryDisplay(ShopItemEntry entry, int entryIndex) {
		ItemStack   copy    = entry.getItem().clone();
		ItemBuilder builder = new ItemBuilder(copy);
		builder.setDisplayName(displayResolver.cleanDisplayName(copy));

		List<String> lore = new ArrayList<>();
		lore.add("&7Index: &f" + entryIndex);
		if (entry.hasPrice()) {
			lore.add("&7Price: &6$" + NumberUtil.valueFormat(entry.getPrice()));
		}
		lore.add("&aL-click &7set price  &cR-click &7remove");

		builder.setLore(lore);
		return builder;
	}

	private ItemBuilder buildCategoryDisplay(SellCategory category) {
		ItemStack icon = category.getItems().isEmpty() ?
		                 material(XMaterial.BOOK, Material.BOOK) :
		                 category.getItems().getFirst().clone();

		ItemBuilder builder = new ItemBuilder(icon);
		builder.setDisplayName("&6" + category.getDisplayName());

		List<String> lore = new ArrayList<>();
		lore.add("&7ID: &f" + category.getId());
		lore.add("&7Base price: &6$" + NumberUtil.valueFormat(category.getBasePrice()));
		lore.add("&7Items: &f" + category.getItems().size());
		lore.add("&aL-click &7edit items + price  &cR-click &7remove");

		builder.setLore(lore);
		return builder;
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private List<SellCategory> deepCopy(List<SellCategory> source) {
		List<SellCategory> copy = new ArrayList<>(source.size());
		for (SellCategory category : source) {
			copy.add(new SellCategory(category.getId(), category.getDisplayName(), category.getBasePrice(),
			                          category.getItems()));
		}
		return copy;
	}

	private List<BarterCategory> deepCopyBarter(List<BarterCategory> source) {
		List<BarterCategory> copy = new ArrayList<>(source.size());
		for (BarterCategory category : source) {
			copy.add(new BarterCategory(category.getId(), category.getDisplayName(), category.getBasePrice(),
			                            category.getItems()));
		}
		return copy;
	}

	private ItemBuilder buildBarterCategoryDisplay(BarterCategory category) {
		ItemStack icon = category.getItems().isEmpty() ?
		                 material(XMaterial.DIAMOND, Material.DIAMOND) :
		                 category.getItems().getFirst().clone();

		ItemBuilder builder = new ItemBuilder(icon);
		builder.setDisplayName("&b" + category.getDisplayName());

		List<String> lore = new ArrayList<>();
		lore.add("&7ID: &f" + category.getId());
		lore.add("&7Base value: &6$" + NumberUtil.valueFormat(category.getBasePrice()));
		lore.add("&7Items: &f" + category.getItems().size());
		lore.add("&aL-click &7edit items + value  &cR-click &7remove");

		builder.setLore(lore);
		return builder;
	}

	// ── Session ──────────────────────────────────────────────────────────

	public static final class Session {
		private final ShopDefinition        original;
		private final InventoryHandler      handler;
		private final List<ShopItemEntry>   buyEntries;
		private final List<SellCategory>    sellCategories;
		private final List<BarterCategory>  barterCategories;
		private final ItemRefresherRegistry refresherRegistry;

		public int       currentPage    = 0;
		public boolean   pendingSubview = false;
		public EntryKind currentKind    = EntryKind.BUY;

		public Session(ShopDefinition original, InventoryHandler handler, List<ShopItemEntry> buyEntries,
		               List<SellCategory> sellCategories, List<BarterCategory> barterCategories,
		               ItemRefresherRegistry refresherRegistry) {
			this.original          = original;
			this.handler           = handler;
			this.buyEntries        = buyEntries;
			this.sellCategories    = sellCategories;
			this.barterCategories  = barterCategories;
			this.refresherRegistry = refresherRegistry;
		}

		public int totalPages() {
			int count = switch (currentKind) {
				case BUY -> buyEntries.size();
				case SELL -> sellCategories.size();
				case BARTER -> barterCategories.size();
			};
			if (count == 0) {
				return 1;
			}
			return (int) Math.ceil(count / (double) ENTRIES_PER_PAGE);
		}

		public void updatePrice(int entryIndex, double newPrice) {
			if (entryIndex < 0 || entryIndex >= buyEntries.size()) {
				return;
			}
			ShopItemEntry existing = buyEntries.get(entryIndex);
			buyEntries.set(entryIndex, new ShopItemEntry(entryIndex, existing.getKind(), existing.getItem(), newPrice));
		}

		public void appendEntry(ShopItemEntry entry) {
			buyEntries.add(entry);
		}

		public ShopItemEntry getEntry(int entryIndex) {
			if (entryIndex < 0 || entryIndex >= buyEntries.size()) {
				return null;
			}
			return buyEntries.get(entryIndex);
		}

		public Double getEffectivePrice(int entryIndex) {
			ShopItemEntry entry = getEntry(entryIndex);
			return entry != null ? entry.getPrice() : null;
		}

		public ShopDefinition buildNewDefinition() {
			List<ShopItemEntry> out = new ArrayList<>(buyEntries.size());
			for (int i = 0; i < buyEntries.size(); i++) {
				ShopItemEntry source = buyEntries.get(i);
				Double        price  = source.getPrice();
				ItemStack     raw    = refresherRegistry.refresh(source.getItem(), null);
				if (price == null) {
					price = DEFAULT_NEW_ENTRY_PRICE;
				}
				out.add(new ShopItemEntry(i, EntryKind.BUY, raw, price));
			}
			return new ShopDefinition(original.getKey(), original.getTitle(), original.getSize(), out,
			                          original.getSellEntries(), sellCategories, barterCategories);
		}
	}

}
