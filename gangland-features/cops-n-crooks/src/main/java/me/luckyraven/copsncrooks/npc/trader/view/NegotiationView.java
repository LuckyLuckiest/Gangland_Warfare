package me.luckyraven.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.events.trader.TraderBuyRequestEvent;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.economy.TraderEconomyContract;
import me.luckyraven.copsncrooks.npc.trader.message.TraderMessageContract;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.flow.MultiPanelInventory;
import me.luckyraven.inventory.flow.Panel;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.shop.ShopItemEntry;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.view.QuantitySelectorView;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;

/**
 * Buy-negotiation panel. Fires {@link TraderBuyRequestEvent} on confirm; hands off to {@link BarterView} /
 * {@link QuantitySelectorView} via {@link MultiPanelInventory#suspend()} + re-entry through
 * {@link MultiPanelInventory#switchTo(String)} on callback.
 */
@RequiredArgsConstructor
public final class NegotiationView implements Panel<TraderFlowSession> {

	private static final int   SIZE            = 54;
	private static final int   SLOT_ITEM       = 22;
	private static final int   SLOT_BUY        = 38;
	private static final int   SLOT_BARTER     = 40;
	private static final int   SLOT_TIP        = 42;
	private static final int   SLOT_BUY_AMOUNT = 47;
	private static final int   SLOT_CANCEL     = 49;
	private static final int[] SLOT_MOOD_RING  = {12, 13, 14, 21, 23, 30, 31, 32};

	private static final SoundConfiguration SOUND_BUY      = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundConfiguration SOUND_OPEN_SUB = vanilla("UI_BUTTON_CLICK", 1.2f);
	private static final SoundConfiguration SOUND_TIP      = vanilla("BLOCK_NOTE_BLOCK_CHIME", 1.5f);
	private static final SoundConfiguration SOUND_CANCEL   = vanilla("ENTITY_VILLAGER_NO", 1.0f);

	private final JavaPlugin            plugin;
	private final MoodService           moodService;
	private final TraderSettings        settings;
	private final TraderMessageContract messages;
	private final TraderEconomyContract economy;
	private final ShopDisplayResolver   displayResolver;
	private final QuantitySelectorView  quantitySelectorView;

	private static SoundConfiguration vanilla(String name, float pitch) {
		return new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, name, 0.6f, pitch);
	}

	@Override
	public int size(TraderFlowSession session) {
		return SIZE;
	}

	@Override
	public String title(TraderFlowSession session) {
		return "&8Negotiation&r &8&l[&b&l" + session.trait.displayName() + "&8&l]";
	}

	@Override
	public void render(MultiPanelInventory<TraderFlowSession> host, InventoryHandler handler, Player viewer,
	                   TraderFlowSession session) {
		BigDecimal    price = currentPrice(session);
		ShopItemEntry entry = session.selectedEntry;

		ItemStack   previewStack = entry.getItem().clone();
		ItemBuilder preview      = new ItemBuilder(previewStack);
		preview.setDisplayName(displayResolver.cleanDisplayName(previewStack))
		       .setLore("&7Asking: &6$" + NumberUtil.valueFormat(price),
		                "&7Trait: &d" + session.trait.displayName(),
		                "&7Mood: " + moodLabel(session.moodMultiplier));
		handler.setItem(SLOT_ITEM, preview, false, (p, inv, b) -> { });

		// Clear the 8 slots around the preview before recolouring — InventoryUtil.aroundSlot skips any non-null slot,
		// so leftover panes from a previous render would otherwise freeze the ring at the old mood colour.
		for (int ring : SLOT_MOOD_RING) handler.getInventory().setItem(ring, null);
		InventoryUtil.aroundSlot(handler, SLOT_ITEM, moodRingMaterial(session.moodMultiplier));

		int         templateAmount = Math.max(1, entry.getItem().getAmount());
		ItemBuilder buy            = new ItemBuilder(material(XMaterial.EMERALD_BLOCK, Material.EMERALD_BLOCK));
		buy.setDisplayName("&a&lBUY")
		   .setLore("&7Pay &6$" + NumberUtil.valueFormat(price) + " &7and receive &f" + templateAmount + " &7items.");
		handler.setItem(SLOT_BUY, buy, false, (p, inv, b) -> onBuy(host, p, session));

		if (entry.getItem().getMaxStackSize() > 1) {
			int         itemsPerCopy = Math.max(1, entry.getItem().getAmount());
			ItemBuilder bulk         = new ItemBuilder(material(XMaterial.CHEST, Material.CHEST));
			bulk.setDisplayName("&a&lBUY AMOUNT")
			    .setLore("&7Choose how many copies to buy.",
			             "&7Per copy: &f" + itemsPerCopy + " items &7for &6$" + NumberUtil.valueFormat(price) + "&7.");
			handler.setItem(SLOT_BUY_AMOUNT, bulk, false, (p, inv, b) -> onBuyAmount(host, p, session));
		}

		BigDecimal  tipAmount = settings.getTipAmount();
		ItemBuilder tip       = new ItemBuilder(material(XMaterial.GOLD_NUGGET, Material.GOLD_NUGGET));
		tip.setDisplayName("&6TIP &e$" + NumberUtil.valueFormat(tipAmount))
		   .setLore("&7Raise trader's mood for a better future price.");
		handler.setItem(SLOT_TIP, tip, false, (p, inv, b) -> onTip(host, p, session, tipAmount));

		boolean canBarter = !session.definition.getBarterCategories().isEmpty() &&
		                    session.trait.profile().allowsBarter();
		if (canBarter) {
			ItemBuilder barter = new ItemBuilder(material(XMaterial.EMERALD, Material.EMERALD));
			barter.setDisplayName("&bBARTER")
			      .setLore("&7Swap items of equal value for this item.", "&7No money changes hands.");
			handler.setItem(SLOT_BARTER, barter, false, (p, inv, b) -> onBarter(host, p, session));
		}

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.ARROW, Material.ARROW));
		cancel.setDisplayName("&cCANCEL").setLore("&7Return to the shop.");
		handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> onCancel(host, p));

		InventoryUtil.fillInventory(handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));
	}

	private BigDecimal currentPrice(TraderFlowSession session) {
		return session.basePrice.multiply(BigDecimal.valueOf(session.moodMultiplier));
	}

	private void onBuy(MultiPanelInventory<TraderFlowSession> host, Player viewer, TraderFlowSession session) {
		SOUND_BUY.playSound(viewer);
		BigDecimal finalPrice = currentPrice(session);

		TraderBuyRequestEvent event = new TraderBuyRequestEvent(viewer, session.trader, session.selectedEntry,
		                                                        finalPrice);
		Bukkit.getPluginManager().callEvent(event);

		// The buy listener owns user-facing messaging (success and failure both go through the contract).
		if (event.isCancelled()) return;

		host.end();
	}

	private void onTip(MultiPanelInventory<TraderFlowSession> host, Player viewer, TraderFlowSession session,
	                   BigDecimal amount) {
		TraderEconomyContract.TipResult result = economy.tryTip(viewer, amount);
		switch (result) {
			case SUCCESS -> {
				SOUND_TIP.playSound(viewer);
				moodService.recordTip(session.trader.getData().getId(), viewer.getUniqueId(), amount,
				                      session.trait.profile());
				viewer.sendMessage(messages.tipSuccess(amount));
				session.moodMultiplier = moodService.priceMultiplier(session.trader.getData().getId(),
				                                                     viewer.getUniqueId(), session.trait.profile());
				host.rerender();
			}
			case INSUFFICIENT_FUNDS -> viewer.sendMessage(messages.tipInsufficientFunds(amount));
			case ECONOMY_ERROR -> SOUND_CANCEL.playSound(viewer);
		}
	}

	private void onBarter(MultiPanelInventory<TraderFlowSession> host, Player viewer, TraderFlowSession session) {
		host.switchTo(TraderFlowSession.PANEL_BARTER);
		playSoundNextTick(viewer, SOUND_OPEN_SUB);
	}

	private void onBuyAmount(MultiPanelInventory<TraderFlowSession> host, Player viewer, TraderFlowSession session) {
		if (quantitySelectorView == null) return;
		if (session.selectedEntry.getItem().getMaxStackSize() <= 1) return;

		BigDecimal unitPrice = currentPrice(session);
		int        maxCopies = 999;
		String     title     = "&8Buy Amount&r &8&l[&b&l" + session.trait.displayName() + "&8&l]";

		host.suspend();
		quantitySelectorView.open(viewer, title, session.selectedEntry.getItem(), unitPrice, 1, maxCopies,
		                          copies -> onBuyAmountConfirmed(host, viewer, session, copies),
		                          () -> {
									  host.resume();
									  host.switchTo(TraderFlowSession.PANEL_NEGOTIATION);
								  });
		playSoundNextTick(viewer, SOUND_OPEN_SUB);
	}

	private void onBuyAmountConfirmed(MultiPanelInventory<TraderFlowSession> host, Player viewer,
	                                  TraderFlowSession session, int copies) {
		SOUND_BUY.playSound(viewer);
		BigDecimal total = currentPrice(session).multiply(BigDecimal.valueOf(copies));

		TraderBuyRequestEvent event = new TraderBuyRequestEvent(viewer, session.trader, session.selectedEntry, total,
		                                                        copies);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			host.resume();
			host.switchTo(TraderFlowSession.PANEL_NEGOTIATION);
			return;
		}

		host.resume();
		host.end();
	}

	private void onCancel(MultiPanelInventory<TraderFlowSession> host, Player viewer) {
		host.back();
		playSoundNextTick(viewer, SOUND_CANCEL);
	}

	/**
	 * Defers the sound by one tick so it plays after the panel swap has settled on the client. Playing the sound in the
	 * same tick as a flow transition makes the client render the audio cue and the inventory change together, which the
	 * viewer experiences as a flicker even when the transition itself is a re-render against the same inventory
	 * handle.
	 */
	private void playSoundNextTick(Player player, SoundConfiguration sound) {
		Bukkit.getScheduler().runTask(plugin, () -> sound.playSound(player));
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private String moodLabel(double multiplier) {
		if (multiplier <= 0.95) return "&aFriendly";
		return "&fNeutral";
	}

	private Material moodRingMaterial(double multiplier) {
		if (multiplier <= 0.95) return Material.LIME_STAINED_GLASS_PANE;
		return Material.WHITE_STAINED_GLASS_PANE;
	}

}
