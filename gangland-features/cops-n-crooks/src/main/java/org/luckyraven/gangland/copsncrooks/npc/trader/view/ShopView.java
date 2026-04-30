package org.luckyraven.gangland.copsncrooks.npc.trader.view;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.trader.config.TraderSettings;
import org.luckyraven.gangland.copsncrooks.npc.trader.mood.MoodService;
import org.luckyraven.gangland.core.ItemBuilder;
import org.luckyraven.gangland.core.configuration.SoundConfiguration;
import org.luckyraven.gangland.core.utilities.NumberUtil;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.inventory.flow.Panel;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.inventory.util.InventoryUtil;
import org.luckyraven.gangland.shop.ShopItemEntry;
import org.luckyraven.gangland.shop.message.ShopDisplayResolver;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Paginated browser panel in the trader flow. Applies the mood-based price multiplier at render time; clicks pivot to
 * the {@link NegotiationView} panel with the selected entry stashed on {@link TraderFlowSession}.
 */
@RequiredArgsConstructor
public final class ShopView implements Panel<TraderFlowSession> {

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
	private static final int SLOT_BACK      = 45;

	private static final SoundConfiguration SOUND_PAGE = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                            "UI_BUTTON_CLICK", 0.6f, 1.2f);

	private final JavaPlugin          plugin;
	private final MoodService         moodService;
	private final TraderSettings      settings;
	private final ShopDisplayResolver displayResolver;

	@Override
	public int size(TraderFlowSession session) {
		return INVENTORY_SIZE;
	}

	@Override
	public String title(TraderFlowSession session) {
		return session.definition.getTitle() + "&r &8&l[&b&l" + session.trait.displayName() + "&8&l]";
	}

	@Override
	public void render(MultiPanelInventory<TraderFlowSession> host, InventoryHandler handler, Player viewer,
	                   TraderFlowSession session) {
		List<ShopItemEntry> entries    = session.definition.getBuyEntries();
		int                 totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) ENTRIES_PER_PAGE));
		session.currentShopPage = Math.clamp(session.currentShopPage, 0, totalPages - 1);

		double multiplier = moodService.priceMultiplier(session.trader.getData().getId(), viewer.getUniqueId(),
		                                                session.trait.profile());

		int base = session.currentShopPage * ENTRIES_PER_PAGE;
		for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
			int entryIndex = base + i;
			if (entryIndex >= entries.size()) break;

			ShopItemEntry entry = entries.get(entryIndex);
			BigDecimal finalPrice = entry.hasPrice()
			                        ? Objects.requireNonNull(entry.getPrice())
			                                 .multiply(BigDecimal.valueOf(multiplier))
			                        : BigDecimal.ZERO;
			ItemBuilder display = buildDisplay(entry, finalPrice);
			int         slot    = INTERIOR_SLOTS[i];

			handler.setItem(slot, display, false, (clicker, inv, builder) -> {
				session.selectedEntry  = entry;
				session.basePrice      = entry.hasPrice() ? entry.getPrice() : BigDecimal.ZERO;
				session.moodMultiplier = multiplier;
				host.switchTo(TraderFlowSession.PANEL_NEGOTIATION);
			});
		}

		renderNavigation(host, handler, viewer, session, totalPages);

		InventoryUtil.createBoarder(handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));
	}

	private void renderNavigation(MultiPanelInventory<TraderFlowSession> host, InventoryHandler handler, Player viewer,
	                              TraderFlowSession session, int totalPages) {
		int currentPage = session.currentShopPage;

		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to menu");
		handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> host.switchTo(TraderFlowSession.PANEL_MODE_SELECT));

		if (currentPage > 0) {
			ItemBuilder prev = new ItemBuilder(Material.ARROW).setDisplayName("&e◄ Previous page")
			                                                  .setLore("&7Go to page " + currentPage + ".");
			handler.setItem(SLOT_PREV, prev, false, (p, inv, b) -> {
				session.currentShopPage = currentPage - 1;
				host.rerender();
				Bukkit.getScheduler().runTask(plugin, () -> SOUND_PAGE.playSound(viewer));
			});
		}

		ItemBuilder info = new ItemBuilder(Material.PAPER);
		info.setDisplayName("&bPage &f" + (currentPage + 1) + "&7/&f" + totalPages)
		    .setLore("&7" + session.definition.getBuyEntries().size() + " item(s) total.");
		handler.setItem(SLOT_PAGE_INFO, info, false, (p, inv, b) -> { });

		if (currentPage < totalPages - 1) {
			ItemBuilder next = new ItemBuilder(Material.ARROW);
			next.setDisplayName("&eNext page ►").setLore("&7Go to page " + (currentPage + 2) + ".");
			handler.setItem(SLOT_NEXT, next, false, (p, inv, b) -> {
				session.currentShopPage = currentPage + 1;
				host.rerender();
				Bukkit.getScheduler().runTask(plugin, () -> SOUND_PAGE.playSound(viewer));
			});
		}
	}

	private ItemBuilder buildDisplay(ShopItemEntry entry, BigDecimal finalPrice) {
		ItemStack   copy    = entry.getItem().clone();
		ItemBuilder builder = new ItemBuilder(copy);
		builder.setDisplayName(displayResolver.cleanDisplayName(copy));

		List<String> existingLore = copy.getItemMeta() != null && copy.getItemMeta().getLore() != null ?
		                            new ArrayList<>(copy.getItemMeta().getLore()) :
		                            new ArrayList<>();

		if (entry.hasPrice()) existingLore.add("&7Price: &6$" + NumberUtil.valueFormat(finalPrice));
		existingLore.add("&8▸ Click to buy");

		builder.setLore(existingLore);
		return builder;
	}

}
