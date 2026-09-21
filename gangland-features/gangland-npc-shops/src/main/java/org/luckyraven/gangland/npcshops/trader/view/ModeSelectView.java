package org.luckyraven.gangland.npcshops.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.npcshops.trader.config.TraderSettings;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.sound.SoundEffect;

/**
 * First panel in the trader flow — asks the viewer whether they want to BUY, SELL, or close. Previously this was a
 * standalone view that opened {@link ShopView} / {@link SellView} via {@code player.closeInventory()} + re-open; it now
 * lives inside the {@link MenuFlow} so the flow session is preserved across transitions.
 */
@RequiredArgsConstructor
public final class ModeSelectView implements Panel<TraderFlowSession> {

	private static final int ROWS       = 3;
	private static final int SLOT_INFO  = 4;
	private static final int SLOT_BUY   = 12;
	private static final int SLOT_SELL  = 14;
	private static final int SLOT_CLOSE = 22;

	private static final SoundEffect SOUND_PICK = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                            "UI_BUTTON_CLICK", 0.6f, 1.4f);

	private final JavaPlugin     plugin;
	private final TraderSettings settings;

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
		ItemBuilder info = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		info.setDisplayName("&b" + session.trait.displayName()).setLore("&7Choose how you want to interact.");
		builder.slot(SLOT_INFO, ItemComponent.of(info));

		ItemBuilder buy = new ItemBuilder(material(XMaterial.EMERALD_BLOCK, Material.EMERALD_BLOCK));
		buy.setDisplayName("&a&lBUY")
		   .setLore("&7Browse what the trader sells.",
		            "&7" + session.definition.getBuyEntries().size() + " item(s) listed.");
		builder.slot(SLOT_BUY, ItemComponent.of(buy).onAnyClick(ctx -> {
			flow.switchTo(TraderFlowSession.PANEL_SHOP);
			playSoundNextTick(ctx.player(), SOUND_PICK);
		}));

		ItemBuilder sell = new ItemBuilder(material(XMaterial.GOLD_BLOCK, Material.GOLD_BLOCK));
		sell.setDisplayName("&6&lSELL")
		    .setLore("&7Offer your items to the trader.",
		             "&7" + session.definition.getSellCategories().size() + " categor(ies).");
		builder.slot(SLOT_SELL, ItemComponent.of(sell).onAnyClick(ctx -> {
			flow.switchTo(TraderFlowSession.PANEL_SELL);
			playSoundNextTick(ctx.player(), SOUND_PICK);
		}));

		ItemBuilder close = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER)).setDisplayName("&cClose");
		builder.slot(SLOT_CLOSE, ItemComponent.of(close).onAnyClick(ctx -> flow.end()));

		builder.fill(FillComponent.of(materialOf(settings.getInventoryFillItem())).name(settings.getInventoryFillName()));
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}

	/**
	 * Defers the sound by one tick so it plays after the panel swap has settled on the client. Playing the sound inline
	 * — in the same tick as {@code switchTo}/{@code end} — makes the client render audio and inventory change together,
	 * which the viewer experiences as a flicker even when the transition itself is smooth.
	 */
	private void playSoundNextTick(Player player, SoundEffect sound) {
		Bukkit.getScheduler().runTask(plugin, () -> sound.playSound(player));
	}

}
