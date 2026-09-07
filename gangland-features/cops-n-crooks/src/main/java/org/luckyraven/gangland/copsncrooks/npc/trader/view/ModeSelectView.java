package org.luckyraven.gangland.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.trader.config.TraderSettings;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.inventory.flow.Panel;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.inventory.util.InventoryUtil;

/**
 * First panel in the trader flow — asks the viewer whether they want to BUY, SELL, or close. Previously this was a
 * standalone view that opened {@link ShopView} / {@link SellView} via {@code player.closeInventory()} + re-open; it now
 * lives inside the {@link MultiPanelInventory} so the flow session is preserved across transitions.
 */
@RequiredArgsConstructor
public final class ModeSelectView implements Panel<TraderFlowSession> {

	private static final int SIZE       = 27;
	private static final int SLOT_INFO  = 4;
	private static final int SLOT_BUY   = 12;
	private static final int SLOT_SELL  = 14;
	private static final int SLOT_CLOSE = 22;

	private static final SoundEffect SOUND_PICK = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                            "UI_BUTTON_CLICK", 0.6f, 1.4f);

	private final JavaPlugin     plugin;
	private final TraderSettings settings;

	@Override
	public int size(TraderFlowSession session) {
		return SIZE;
	}

	@Override
	public String title(TraderFlowSession session) {
		return session.definition.getTitle() + "&r &8&l[&b&l" + session.trait.displayName() + "&8&l]";
	}

	@Override
	public void render(MultiPanelInventory<TraderFlowSession> host, InventoryHandler handler, Player viewer,
	                   TraderFlowSession session) {
		ItemBuilder info = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		info.setDisplayName("&b" + session.trait.displayName()).setLore("&7Choose how you want to interact.");
		handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });

		ItemBuilder buy = new ItemBuilder(material(XMaterial.EMERALD_BLOCK, Material.EMERALD_BLOCK));
		buy.setDisplayName("&a&lBUY")
		   .setLore("&7Browse what the trader sells.",
		            "&7" + session.definition.getBuyEntries().size() + " item(s) listed.");
		handler.setItem(SLOT_BUY, buy, false, (p, inv, b) -> {
			host.switchTo(TraderFlowSession.PANEL_SHOP);
			playSoundNextTick(p, SOUND_PICK);
		});

		ItemBuilder sell = new ItemBuilder(material(XMaterial.GOLD_BLOCK, Material.GOLD_BLOCK));
		sell.setDisplayName("&6&lSELL")
		    .setLore("&7Offer your items to the trader.",
		             "&7" + session.definition.getSellCategories().size() + " categor(ies).");
		handler.setItem(SLOT_SELL, sell, false, (p, inv, b) -> {
			host.switchTo(TraderFlowSession.PANEL_SELL);
			playSoundNextTick(p, SOUND_PICK);
		});

		ItemBuilder close = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER)).setDisplayName("&cClose");
		handler.setItem(SLOT_CLOSE, close, false, (p, inv, b) -> host.end());

		InventoryUtil.fillInventory(handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
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
