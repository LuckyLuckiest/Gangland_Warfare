package me.luckyraven.copsncrooks.npc.trader.view;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.ShopItemEntry;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Trader-specific browser view. Applies mood-based price multipliers, routes clicks into {@link NegotiationView}. Moved
 * from gangland-impl's shop package per the feedback that all trader NPC code lives in cops-n-crooks.
 */
@RequiredArgsConstructor
public final class TraderShopView {

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

	private final JavaPlugin          plugin;
	private final MoodService         moodService;
	private final NegotiationView     negotiationView;
	private final TraderSettings      settings;
	private final ShopDisplayResolver displayResolver;

	public void open(Player viewer, TraderNpc trader, ShopDefinition def, TraderTraitDefinition trait) {
		openPage(viewer, trader, def, trait, 0);
	}

	private void openPage(Player viewer, TraderNpc trader, ShopDefinition def,
	                      TraderTraitDefinition trait, int page) {
		String title = def.getTitle() + " — " + trait.displayName();

		InventoryHandler handler = new InventoryHandler(plugin, title, INVENTORY_SIZE, viewer);

		List<ShopItemEntry> entries = def.getBuyEntries();
		int totalPages = Math.max(1,
		                          (int) Math.ceil(entries.size() / (double) ENTRIES_PER_PAGE));
		int currentPage = Math.max(0, Math.min(page, totalPages - 1));

		double multiplier = moodService.priceMultiplier(trader.getData().getId(), viewer.getUniqueId(),
		                                                trait.profile());

		int base = currentPage * ENTRIES_PER_PAGE;
		for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
			int entryIndex = base + i;
			if (entryIndex >= entries.size()) break;

			ShopItemEntry entry      = entries.get(entryIndex);
			double        finalPrice = entry.hasPrice() ? entry.getPrice() * multiplier : 0D;
			ItemBuilder   display    = buildDisplay(entry, finalPrice);
			int           slot       = INTERIOR_SLOTS[i];

			handler.setItem(slot, display, false, (clicker, inv, builder) -> {
				clicker.closeInventory();
				Bukkit.getScheduler().runTask(plugin, () ->
						negotiationView.open(clicker, trader, entry, trait,
						                     () -> openPage(clicker, trader, def, trait, currentPage)));
			});
		}

		renderNavigation(viewer, trader, def, trait, handler, currentPage, totalPages);

		InventoryUtil.createBoarder(handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));
		handler.open(viewer);
	}

	private void renderNavigation(Player viewer, TraderNpc trader, ShopDefinition def,
	                              TraderTraitDefinition trait, InventoryHandler handler,
	                              int currentPage, int totalPages) {
		if (currentPage > 0) {
			ItemBuilder prev = new ItemBuilder(Material.ARROW)
					.setDisplayName("&e◄ Previous page")
					.setLore("&7Go to page " + currentPage + ".");
			handler.setItem(SLOT_PREV, prev, false, (p, inv, b) -> {
				SOUND_PAGE.playSound(viewer);
				openPage(viewer, trader, def, trait, currentPage - 1);
			});
		}

		ItemBuilder info = new ItemBuilder(Material.PAPER)
				.setDisplayName("&bPage &f" + (currentPage + 1) + "&7/&f" + totalPages)
				.setLore("&7" + def.getBuyEntries().size() + " item(s) total.");
		handler.setItem(SLOT_PAGE_INFO, info, false, (p, inv, b) -> { });

		if (currentPage < totalPages - 1) {
			ItemBuilder next = new ItemBuilder(Material.ARROW)
					.setDisplayName("&eNext page ►")
					.setLore("&7Go to page " + (currentPage + 2) + ".");
			handler.setItem(SLOT_NEXT, next, false, (p, inv, b) -> {
				SOUND_PAGE.playSound(viewer);
				openPage(viewer, trader, def, trait, currentPage + 1);
			});
		}
	}

	private ItemBuilder buildDisplay(ShopItemEntry entry, double finalPrice) {
		ItemStack   copy    = entry.getItem().clone();
		ItemBuilder builder = new ItemBuilder(copy);
		builder.setDisplayName(displayResolver.cleanDisplayName(copy));

		List<String> existingLore = copy.getItemMeta() != null && copy.getItemMeta().getLore() != null
		                            ? new ArrayList<>(copy.getItemMeta().getLore())
		                            : new ArrayList<>();

		if (entry.hasPrice()) {
			existingLore.add("&7Price: &6$" + formatPrice(finalPrice));
		}
		if (entry.hasBarter()) {
			existingLore.add("&7Barter: &b" + entry.getTradeFor().getType().name()
			                 + " &7×&b" + entry.getTradeFor().getAmount());
		}
		existingLore.add("&8▸ Click to buy");

		builder.setLore(existingLore);
		return builder;
	}

	private String formatPrice(double price) {
		if (price == Math.floor(price)) return String.valueOf((long) price);
		return String.format("%.2f", price);
	}

}
