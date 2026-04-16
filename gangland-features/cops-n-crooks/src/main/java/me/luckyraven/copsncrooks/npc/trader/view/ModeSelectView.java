package me.luckyraven.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

@RequiredArgsConstructor
public final class ModeSelectView {

	private static final int SIZE       = 27;
	private static final int SLOT_INFO  = 4;
	private static final int SLOT_BUY   = 12;
	private static final int SLOT_SELL  = 14;
	private static final int SLOT_CLOSE = 22;

	private static final SoundConfiguration SOUND_PICK = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                            "UI_BUTTON_CLICK", 0.6f, 1.4f);

	private final JavaPlugin     plugin;
	private final TraderSettings settings;
	private       ShopView       shopView;
	private       SellView       sellView;

	public void setSubViews(ShopView shopView, SellView sellView) {
		this.shopView = shopView;
		this.sellView = sellView;
	}

	public void open(Player viewer, TraderNpc trader, ShopDefinition def, TraderTraitDefinition trait) {
		String           title   = def.getTitle() + "&r &8&l[&b&l" + trait.displayName() + "&8&l]";
		InventoryHandler handler = new InventoryHandler(plugin, title, SIZE, viewer);

		ItemBuilder info = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		info.setDisplayName("&b" + trait.displayName()).setLore("&7Choose how you want to interact.");
		handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });

		ItemBuilder buy = new ItemBuilder(material(XMaterial.EMERALD_BLOCK, Material.EMERALD_BLOCK));
		buy.setDisplayName("&a&lBUY")
		   .setLore("&7Browse what the trader sells.", "&7" + def.getBuyEntries().size() + " item(s) listed.");
		handler.setItem(SLOT_BUY, buy, false, (p, inv, b) -> {
			SOUND_PICK.playSound(p);
			p.closeInventory();
			if (shopView != null) {
				shopView.open(p, trader, def, trait);
			}
		});

		ItemBuilder sell = new ItemBuilder(material(XMaterial.GOLD_BLOCK, Material.GOLD_BLOCK));
		sell.setDisplayName("&6&lSELL")
		    .setLore("&7Offer your items to the trader.", "&7" + def.getSellCategories().size() + " categor(ies).");
		handler.setItem(SLOT_SELL, sell, false, (p, inv, b) -> {
			SOUND_PICK.playSound(p);
			p.closeInventory();
			if (sellView != null) {
				sellView.open(p, trader, def, trait);
			}
		});

		ItemBuilder close = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER)).setDisplayName("&cClose");
		handler.setItem(SLOT_CLOSE, close, false, (p, inv, b) -> p.closeInventory());

		InventoryUtil.fillInventory(handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		handler.open(viewer);
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

}
