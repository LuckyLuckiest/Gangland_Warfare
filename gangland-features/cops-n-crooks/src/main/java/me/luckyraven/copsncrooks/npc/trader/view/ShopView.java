package me.luckyraven.copsncrooks.npc.trader.view;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.ShopItemEntry;
import me.luckyraven.shop.message.ShopDisplayResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Trader-specific browser view. Applies mood-based price multipliers, routes clicks into {@link NegotiationView}. Moved
 * from gangland-impl's shop package per the feedback that all trader NPC code lives in cops-n-crooks.
 */
@RequiredArgsConstructor
public final class ShopView {

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
	private final NegotiationView     negotiationView;
	private final TraderSettings      settings;
	private final ShopDisplayResolver displayResolver;
	@Setter
	private       ModeSelectView      modeSelectView;

	public void open(Player viewer, TraderNpc trader, ShopDefinition def, TraderTraitDefinition trait) {
		openPage(viewer, trader, def, trait, 0);
	}

	private void openPage(Player viewer, TraderNpc trader, ShopDefinition def, TraderTraitDefinition trait, int page) {
		String title = def.getTitle() + "&r &8&l[&b&l" + trait.displayName() + "&8&l]";

		InventoryHandler handler = new InventoryHandler(plugin, title, INVENTORY_SIZE, viewer);

		List<ShopItemEntry> entries     = def.getBuyEntries();
		int                 totalPages  = Math.max(1, (int) Math.ceil(entries.size() / (double) ENTRIES_PER_PAGE));
		int                 currentPage = Math.clamp(page, 0, totalPages - 1);

		double multiplier = moodService.priceMultiplier(trader.getData().getId(), viewer.getUniqueId(),
		                                                trait.profile());

		int base = currentPage * ENTRIES_PER_PAGE;
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
				clicker.closeInventory();
				Bukkit.getScheduler()
				      .runTask(plugin, () -> negotiationView.open(clicker, trader, def, entry, trait,
				                                                  () -> openPage(clicker, trader, def, trait,
				                                                                 currentPage)));
			});
		}

		renderNavigation(viewer, trader, def, trait, handler, currentPage, totalPages);

		InventoryUtil.createBoarder(handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));
		handler.open(viewer);
	}

	private void renderNavigation(Player viewer, TraderNpc trader, ShopDefinition def, TraderTraitDefinition trait,
	                              InventoryHandler handler, int currentPage, int totalPages) {
		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to menu");
		handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> {
			viewer.closeInventory();
			if (modeSelectView != null) {
				Bukkit.getScheduler().runTask(plugin, () -> modeSelectView.open(viewer, trader, def, trait));
			}
		});

		if (currentPage > 0) {
			ItemBuilder prev = new ItemBuilder(Material.ARROW).setDisplayName("&e◄ Previous page")
			                                                  .setLore("&7Go to page " + currentPage + ".");
			handler.setItem(SLOT_PREV, prev, false, (p, inv, b) -> {
				SOUND_PAGE.playSound(viewer);
				openPage(viewer, trader, def, trait, currentPage - 1);
			});
		}

		ItemBuilder info = new ItemBuilder(Material.PAPER);
		info.setDisplayName("&bPage &f" + (currentPage + 1) + "&7/&f" + totalPages)
		    .setLore("&7" + def.getBuyEntries().size() + " item(s) total.");
		handler.setItem(SLOT_PAGE_INFO, info, false, (p, inv, b) -> { });

		if (currentPage < totalPages - 1) {
			ItemBuilder next = new ItemBuilder(Material.ARROW);
			next.setDisplayName("&eNext page ►").setLore("&7Go to page " + (currentPage + 2) + ".");
			handler.setItem(SLOT_NEXT, next, false, (p, inv, b) -> {
				SOUND_PAGE.playSound(viewer);
				openPage(viewer, trader, def, trait, currentPage + 1);
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

		if (entry.hasPrice()) {
			existingLore.add("&7Price: &6$" + NumberUtil.valueFormat(finalPrice));
		}
		existingLore.add("&8▸ Click to buy");

		builder.setLore(existingLore);
		return builder;
	}

}
