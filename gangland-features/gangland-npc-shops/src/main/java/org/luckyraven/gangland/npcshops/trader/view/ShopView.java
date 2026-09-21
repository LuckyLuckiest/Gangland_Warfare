package org.luckyraven.gangland.npcshops.trader.view;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.npcshops.trader.config.TraderSettings;
import org.luckyraven.gangland.npcshops.trader.mood.MoodService;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.BorderComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.NumberUtil;
import com.cryptomorin.xseries.XMaterial;
import org.luckyraven.keystone.shop.ShopItemEntry;
import org.luckyraven.keystone.shop.message.ShopDisplayResolver;

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

	private static final int   ROWS             = 6;
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

	private static final SoundEffect SOUND_PAGE = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                            "UI_BUTTON_CLICK", 0.6f, 1.2f);

	private final JavaPlugin          plugin;
	private final MoodService         moodService;
	private final TraderSettings      settings;
	private final ShopDisplayResolver displayResolver;

	@Override
	public int rows(TraderFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(TraderFlowSession session) {
		return session.definition.getTitle() + "&r &8&l[&b&l" + session.trait.displayName() + "&8&l]";
	}

	@Override
	public void render(MenuFlow<TraderFlowSession> flow, ChestMenuBuilder builder, TraderFlowSession session) {
		List<ShopItemEntry> entries    = session.definition.getBuyEntries();
		int                 totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) ENTRIES_PER_PAGE));
		session.currentShopPage = Math.max(0, Math.min(session.currentShopPage, totalPages - 1));

		double multiplier = moodService.priceMultiplier(session.trader.getData().getId(), flow.viewer().getUniqueId(),
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

			builder.slot(slot, ItemComponent.of(display).onAnyClick(ctx -> {
				session.selectedEntry  = entry;
				session.basePrice      = entry.hasPrice() ? entry.getPrice() : BigDecimal.ZERO;
				session.moodMultiplier = multiplier;
				flow.switchTo(TraderFlowSession.PANEL_NEGOTIATION);
			}));
		}

		renderNavigation(flow, builder, session, totalPages);

		builder.border(BorderComponent.of(materialOf(settings.getInventoryFillItem())).name(settings.getInventoryFillName()));
	}

	private void renderNavigation(MenuFlow<TraderFlowSession> flow, ChestMenuBuilder builder, TraderFlowSession session,
	                              int totalPages) {
		int currentPage = session.currentShopPage;

		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to menu");
		builder.slot(SLOT_BACK, ItemComponent.of(back).onAnyClick(ctx -> flow.switchTo(TraderFlowSession.PANEL_MODE_SELECT)));

		if (currentPage > 0) {
			ItemBuilder prev = new ItemBuilder(Material.ARROW).setDisplayName("&e◄ Previous page")
			                                                  .setLore("&7Go to page " + currentPage + ".");
			builder.slot(SLOT_PREV, ItemComponent.of(prev).onAnyClick(ctx -> {
				session.currentShopPage = currentPage - 1;
				flow.rerender();
				Bukkit.getScheduler().runTask(plugin, () -> SOUND_PAGE.playSound(ctx.player()));
			}));
		}

		ItemBuilder info = new ItemBuilder(Material.PAPER);
		info.setDisplayName("&bPage &f" + (currentPage + 1) + "&7/&f" + totalPages)
		    .setLore("&7" + session.definition.getBuyEntries().size() + " item(s) total.");
		builder.slot(SLOT_PAGE_INFO, ItemComponent.of(info));

		if (currentPage < totalPages - 1) {
			ItemBuilder next = new ItemBuilder(Material.ARROW);
			next.setDisplayName("&eNext page ►").setLore("&7Go to page " + (currentPage + 2) + ".");
			builder.slot(SLOT_NEXT, ItemComponent.of(next).onAnyClick(ctx -> {
				session.currentShopPage = currentPage + 1;
				flow.rerender();
				Bukkit.getScheduler().runTask(plugin, () -> SOUND_PAGE.playSound(ctx.player()));
			}));
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

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}

}
