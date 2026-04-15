package me.luckyraven.shop.view;

import lombok.RequiredArgsConstructor;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.shop.EntryKind;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.ShopItemEntry;
import me.luckyraven.shop.config.ShopUiSettings;
import me.luckyraven.shop.event.ShopEditedEvent;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.message.ShopMessageContract;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

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

	private static final int SLOT_PREV      = 48;
	private static final int SLOT_PAGE_INFO = 49;
	private static final int SLOT_NEXT      = 50;

	private static final SoundConfiguration SOUND_PAGE = new SoundConfiguration(
			SoundConfiguration.SoundType.VANILLA, "UI_BUTTON_CLICK", 0.6f, 1.2f);

	private final JavaPlugin            plugin;
	private final PriceEditorView       priceEditorView;
	private final ItemRefresherRegistry refresherRegistry;
	private final ShopMessageContract   messages;
	private final ShopUiSettings        uiSettings;
	private final ShopDisplayResolver   displayResolver;

	private final Map<Player, Session> active = new WeakHashMap<>();

	public void open(Player admin, ShopDefinition def) {
		String           title   = "&8Admin: &f" + def.getTitle();
		InventoryHandler handler = new InventoryHandler(plugin, title, INVENTORY_SIZE, admin);

		List<ShopItemEntry> entries = new ArrayList<>(def.getBuyEntries());
		Session             session = new Session(def, handler, entries, refresherRegistry);
		active.put(admin, session);

		render(admin, session);
		handler.open(admin);

		Bukkit.getPluginManager().registerEvents(new SessionListener(admin, session), plugin);
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void render(Player admin, Session session) {
		for (int i = 0; i < INVENTORY_SIZE; i++) {
			session.handler.getInventory().setItem(i, null);
		}

		int base = session.currentPage * ENTRIES_PER_PAGE;
		for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
			int entryIndex = base + i;
			int slot       = INTERIOR_SLOTS[i];

			if (entryIndex >= session.entries.size()) continue;

			ShopItemEntry entry      = session.entries.get(entryIndex);
			final int     finalIndex = entryIndex;
			session.handler.setItem(slot, buildAdminDisplay(entry, entryIndex), false,
			                        (player, inv, b) -> onLeftClick(admin, finalIndex),
			                        (player, inv, b) -> onRightClick(admin, finalIndex));
		}

		renderNavigation(admin, session);

		InventoryUtil.createBoarder(session.handler,
		                            new Fill(uiSettings.getInventoryFillName(), uiSettings.getInventoryFillItem()));
	}

	private void renderNavigation(Player admin, Session session) {
		int totalPages = session.totalPages();
		int current    = session.currentPage;

		if (current > 0) {
			ItemBuilder prev = new ItemBuilder(Material.ARROW)
					.setDisplayName("&e◄ Previous page")
					.setLore("&7Go to page " + current + ".");
			session.handler.setItem(SLOT_PREV, prev, false,
			                        (p, inv, b) -> changePage(admin, session, current - 1));
		}

		ItemBuilder info = new ItemBuilder(Material.PAPER)
				.setDisplayName("&bPage &f" + (current + 1) + "&7/&f" + totalPages)
				.setLore("&7" + session.entries.size() + " item(s) total.",
				         "&7" + ENTRIES_PER_PAGE + " slots per page.");
		session.handler.setItem(SLOT_PAGE_INFO, info, false, (p, inv, b) -> { });

		boolean hasNext = current < totalPages - 1 || isLastPageFull(session);
		if (hasNext) {
			ItemBuilder next = new ItemBuilder(Material.ARROW)
					.setDisplayName("&eNext page ►")
					.setLore("&7Go to page " + (current + 2) + ".");
			session.handler.setItem(SLOT_NEXT, next, false,
			                        (p, inv, b) -> changePage(admin, session, current + 1));
		}
	}

	private boolean isLastPageFull(Session session) {
		return !session.entries.isEmpty() && session.entries.size() % ENTRIES_PER_PAGE == 0;
	}

	private void changePage(Player admin, Session session, int newPage) {
		int maxPage = session.totalPages() - 1 + (isLastPageFull(session) ? 1 : 0);
		int clamped = Math.clamp(newPage, 0, maxPage);
		if (clamped == session.currentPage) return;
		session.currentPage = clamped;
		SOUND_PAGE.playSound(admin);
		render(admin, session);
	}

	// ── Click handlers ───────────────────────────────────────────────────

	private void onLeftClick(Player admin, int entryIndex) {
		Session session = active.get(admin);
		if (session == null) return;
		if (entryIndex < 0 || entryIndex >= session.entries.size()) return;

		ShopItemEntry entry    = session.entries.get(entryIndex);
		Double        current  = entry.getPrice();
		double        original = current != null ? current : DEFAULT_NEW_ENTRY_PRICE;

		session.pendingSubview = true;

		int finalIndex = entryIndex;
		Runnable reopen = () -> {
			session.pendingSubview = false;
			render(admin, session);
			session.handler.open(admin);
		};

		priceEditorView.open(admin, session, finalIndex, entry.getItem(), original, reopen);
	}

	private void onRightClick(Player admin, int entryIndex) {
		Session s = active.get(admin);
		if (s == null) return;
		if (entryIndex < 0 || entryIndex >= s.entries.size()) return;

		s.entries.remove(entryIndex);
		int maxPage = s.totalPages() - 1;
		if (s.currentPage > maxPage) s.currentPage = maxPage;

		render(admin, s);
		admin.sendMessage(messages.shopAdminEntryRemoved(entryIndex));
	}

	// ── Display ──────────────────────────────────────────────────────────

	private ItemBuilder buildAdminDisplay(ShopItemEntry entry, int entryIndex) {
		ItemStack   copy    = entry.getItem().clone();
		ItemBuilder builder = new ItemBuilder(copy);
		builder.setDisplayName(displayResolver.cleanDisplayName(copy));

		List<String> lore = new ArrayList<>();
		lore.add("&7Index: &f" + entryIndex);
		if (entry.hasPrice()) lore.add("&7Price: &6$" + entry.getPrice());
		if (entry.hasBarter()) lore.add("&7Trade-for: &b" + entry.getTradeFor().getType().name());
		lore.add("&aL-click &7set price  &cR-click &7remove");

		builder.setLore(lore);
		return builder;
	}

	// ── Session ──────────────────────────────────────────────────────────

	public static final class Session {
		private final ShopDefinition        original;
		private final InventoryHandler      handler;
		private final List<ShopItemEntry>   entries;
		private final ItemRefresherRegistry refresherRegistry;

		public int     currentPage    = 0;
		public boolean pendingSubview = false;

		public Session(ShopDefinition original, InventoryHandler handler,
		               List<ShopItemEntry> entries, ItemRefresherRegistry refresherRegistry) {
			this.original          = original;
			this.handler           = handler;
			this.entries           = entries;
			this.refresherRegistry = refresherRegistry;
		}

		public int totalPages() {
			if (entries.isEmpty()) return 1;
			return (int) Math.ceil(entries.size() / (double) ENTRIES_PER_PAGE);
		}

		public void updatePrice(int entryIndex, double newPrice) {
			if (entryIndex < 0 || entryIndex >= entries.size()) return;
			ShopItemEntry existing = entries.get(entryIndex);
			entries.set(entryIndex, new ShopItemEntry(entryIndex, existing.getKind(), existing.getItem(),
			                                          newPrice, existing.getTradeFor()));
		}

		public void appendEntry(ShopItemEntry entry) {
			entries.add(entry);
		}

		public ShopItemEntry getEntry(int entryIndex) {
			if (entryIndex < 0 || entryIndex >= entries.size()) return null;
			return entries.get(entryIndex);
		}

		public Double getEffectivePrice(int entryIndex) {
			ShopItemEntry entry = getEntry(entryIndex);
			return entry != null ? entry.getPrice() : null;
		}

		public ShopDefinition buildNewDefinition() {
			List<ShopItemEntry> out = new ArrayList<>(entries.size());
			for (int i = 0; i < entries.size(); i++) {
				ShopItemEntry source = entries.get(i);
				Double        price  = source.getPrice();
				ItemStack     barter = source.getTradeFor();
				ItemStack     raw    = refresherRegistry.refresh(source.getItem(), null);
				if (price == null && barter == null) price = DEFAULT_NEW_ENTRY_PRICE;
				out.add(new ShopItemEntry(i, EntryKind.BUY, raw, price, barter));
			}
			return new ShopDefinition(original.getKey(), original.getTitle(), original.getSize(),
			                          out, original.getSellEntries());
		}
	}

	// ── Session-scoped listener ──────────────────────────────────────────

	private final class SessionListener implements Listener {
		private final Player  admin;
		private final Session session;

		SessionListener(Player admin, Session session) {
			this.admin   = admin;
			this.session = session;
		}

		@EventHandler(priority = EventPriority.HIGH)
		public void onClick(InventoryClickEvent event) {
			if (event.getWhoClicked() != admin) return;
			if (event.getInventory() != session.handler.getInventory()) return;

			Inventory top    = event.getView().getTopInventory();
			Inventory bottom = event.getView().getBottomInventory();
			ClickType click  = event.getClick();

			if ((click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT)
			    && event.getClickedInventory() == bottom) {
				ItemStack src = event.getCurrentItem();
				if (src == null || src.getType().isAir()) return;

				event.setCancelled(true);
				appendEntryAndNavigate(src);
				return;
			}

			if (event.getClickedInventory() == top) {
				ItemStack cursor = event.getCursor();
				if (cursor == null || cursor.getType().isAir()) return;

				event.setCancelled(true);

				int slot = event.getSlot();
				if (!isInterior(slot)) return;

				appendEntryAndNavigate(cursor);
			}
		}

		@EventHandler
		public void onClose(InventoryCloseEvent event) {
			if (event.getPlayer() != admin) return;
			if (event.getInventory() != session.handler.getInventory()) return;
			if (session.pendingSubview) return;

			ShopDefinition updated = session.buildNewDefinition();
			Bukkit.getPluginManager().callEvent(new ShopEditedEvent(admin, updated));
			active.remove(admin);
			HandlerList.unregisterAll(this);
		}

		private void appendEntryAndNavigate(ItemStack source) {
			ItemStack refreshed = refresherRegistry.refresh(source, null);
			int       newIndex  = session.entries.size();
			ShopItemEntry entry = new ShopItemEntry(newIndex, EntryKind.BUY, refreshed.clone(),
			                                        DEFAULT_NEW_ENTRY_PRICE, null);

			session.appendEntry(entry);
			session.currentPage = newIndex / ENTRIES_PER_PAGE;

			render(admin, session);
			admin.sendMessage(messages.shopAdminEntryAdded(displayResolver.cleanDisplayName(refreshed),
			                                               newIndex, session.currentPage + 1));
		}

		private boolean isInterior(int slot) {
			for (int s : INTERIOR_SLOTS) if (s == slot) return true;
			return false;
		}
	}

}
