package org.luckyraven.gangland.npcshops.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.npcshops.events.trader.TraderBuyRequestEvent;
import org.luckyraven.gangland.npcshops.trader.config.TraderSettings;
import org.luckyraven.gangland.npcshops.trader.economy.TraderEconomyContract;
import org.luckyraven.gangland.npcshops.trader.message.TraderMessageContract;
import org.luckyraven.gangland.npcshops.trader.mood.MoodService;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.keystone.shop.ShopItemEntry;
import org.luckyraven.keystone.shop.message.ShopDisplayResolver;

import java.math.BigDecimal;

/**
 * Buy-negotiation panel. Fires {@link TraderBuyRequestEvent} on confirm. Hands off to {@link BarterView} or
 * {@link QuantitySelectorView} via {@link MenuFlow#switchTo(String)} — both are panels in the trader flow, so the
 * transitions are same-handle re-renders (no inventory swap) where size + title permit.
 */
@RequiredArgsConstructor
public final class NegotiationView implements Panel<TraderFlowSession> {

	private static final int   ROWS            = 6;
	private static final int   SLOT_ITEM       = 22;
	private static final int   SLOT_BUY        = 38;
	private static final int   SLOT_BARTER     = 40;
	private static final int   SLOT_TIP        = 42;
	private static final int   SLOT_BUY_AMOUNT = 47;
	private static final int   SLOT_CANCEL     = 49;
	private static final int[] SLOT_MOOD_RING  = {12, 13, 14, 21, 23, 30, 31, 32};

	private static final SoundEffect SOUND_BUY      = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundEffect SOUND_OPEN_SUB = vanilla("UI_BUTTON_CLICK", 1.2f);
	private static final SoundEffect SOUND_TIP      = vanilla("BLOCK_NOTE_BLOCK_CHIME", 1.5f);
	private static final SoundEffect SOUND_CANCEL   = vanilla("ENTITY_VILLAGER_NO", 1.0f);

	private final JavaPlugin            plugin;
	private final MoodService           moodService;
	private final TraderSettings        settings;
	private final TraderMessageContract messages;
	private final TraderEconomyContract economy;
	private final ShopDisplayResolver   displayResolver;

	private static SoundEffect vanilla(String name, float pitch) {
		return new SoundEffect(SoundEffect.SoundType.VANILLA, name, 0.6f, pitch);
	}

	@Override
	public int rows(TraderFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(TraderFlowSession session) {
		return "&8Negotiation&r &8&l[&b&l" + session.trait.displayName() + "&8&l]";
	}

	@Override
	public void render(MenuFlow<TraderFlowSession> flow, ChestMenuBuilder builder, TraderFlowSession session) {
		BigDecimal    price = currentPrice(session);
		ShopItemEntry entry = session.selectedEntry;

		ItemStack   previewStack = entry.getItem().clone();
		ItemBuilder preview      = new ItemBuilder(previewStack);
		preview.setDisplayName(displayResolver.cleanDisplayName(previewStack))
		       .setLore("&7Asking: &6$" + NumberUtil.valueFormat(price),
		                "&7Trait: &d" + session.trait.displayName(),
		                "&7Mood: " + moodLabel(session.moodMultiplier));
		builder.slot(SLOT_ITEM, ItemComponent.of(preview));

		// The builder is rebuilt fresh every render() call, so — unlike the old InventoryHandler.aroundSlot, which
		// had to skip already-occupied slots and needed an explicit clear-first pass to recolour on a re-render —
		// the ring slots can just be set directly here every time.
		Material ringMaterial = moodRingMaterial(session.moodMultiplier);
		for (int ring : SLOT_MOOD_RING) {
			if (ring == SLOT_ITEM) continue;
			builder.slot(ring, ItemComponent.of(ringMaterial));
		}

		int         templateAmount = Math.max(1, entry.getItem().getAmount());
		ItemBuilder buy            = new ItemBuilder(material(XMaterial.EMERALD_BLOCK, Material.EMERALD_BLOCK));
		buy.setDisplayName("&a&lBUY")
		   .setLore("&7Pay &6$" + NumberUtil.valueFormat(price) + " &7and receive &f" + templateAmount + " &7items.");
		builder.slot(SLOT_BUY, ItemComponent.of(buy).onAnyClick(ctx -> onBuy(flow, ctx.player(), session)));

		if (entry.getItem().getMaxStackSize() > 1) {
			int         itemsPerCopy = Math.max(1, entry.getItem().getAmount());
			ItemBuilder bulk         = new ItemBuilder(material(XMaterial.CHEST, Material.CHEST));
			bulk.setDisplayName("&a&lBUY AMOUNT")
			    .setLore("&7Choose how many copies to buy.",
			             "&7Per copy: &f" + itemsPerCopy + " items &7for &6$" + NumberUtil.valueFormat(price) + "&7.");
			builder.slot(SLOT_BUY_AMOUNT,
			            ItemComponent.of(bulk).onAnyClick(ctx -> onBuyAmount(flow, ctx.player(), session)));
		}

		BigDecimal  tipAmount = settings.getTipAmount();
		ItemBuilder tip       = new ItemBuilder(material(XMaterial.GOLD_NUGGET, Material.GOLD_NUGGET));
		tip.setDisplayName("&6TIP &e$" + NumberUtil.valueFormat(tipAmount))
		   .setLore("&7Raise trader's mood for a better future price.");
		builder.slot(SLOT_TIP, ItemComponent.of(tip).onAnyClick(ctx -> onTip(flow, ctx.player(), session, tipAmount)));

		boolean canBarter = !session.definition.getBarterCategories().isEmpty() &&
		                    session.trait.profile().allowsBarter();
		if (canBarter) {
			ItemBuilder barter = new ItemBuilder(material(XMaterial.EMERALD, Material.EMERALD));
			barter.setDisplayName("&bBARTER")
			      .setLore("&7Swap items of equal value for this item.", "&7No money changes hands.");
			builder.slot(SLOT_BARTER, ItemComponent.of(barter).onAnyClick(ctx -> onBarter(flow, ctx.player(), session)));
		}

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.ARROW, Material.ARROW));
		cancel.setDisplayName("&cCANCEL").setLore("&7Return to the shop.");
		builder.slot(SLOT_CANCEL, ItemComponent.of(cancel).onAnyClick(ctx -> onCancel(flow, ctx.player())));

		builder.fill(FillComponent.of(materialOf(settings.getInventoryFillItem())).name(settings.getInventoryFillName()));
	}

	private BigDecimal currentPrice(TraderFlowSession session) {
		return session.basePrice.multiply(BigDecimal.valueOf(session.moodMultiplier));
	}

	private void onBuy(MenuFlow<TraderFlowSession> flow, Player viewer, TraderFlowSession session) {
		SOUND_BUY.playSound(viewer);
		BigDecimal finalPrice = currentPrice(session);

		TraderBuyRequestEvent event = new TraderBuyRequestEvent(viewer, session.trader, session.selectedEntry,
		                                                        finalPrice);
		Bukkit.getPluginManager().callEvent(event);

		// The buy listener owns user-facing messaging (success and failure both go through the contract).
		if (event.isCancelled()) return;

		flow.end();
	}

	private void onTip(MenuFlow<TraderFlowSession> flow, Player viewer, TraderFlowSession session, BigDecimal amount) {
		TraderEconomyContract.TipResult result = economy.tryTip(viewer, amount);
		switch (result) {
			case SUCCESS -> {
				SOUND_TIP.playSound(viewer);
				moodService.recordTip(session.trader.getData().getId(), viewer.getUniqueId(), amount,
				                      session.trait.profile());
				viewer.sendMessage(messages.tipSuccess(amount));
				session.moodMultiplier = moodService.priceMultiplier(session.trader.getData().getId(),
				                                                     viewer.getUniqueId(), session.trait.profile());
				flow.rerender();
			}
			case INSUFFICIENT_FUNDS -> viewer.sendMessage(messages.tipInsufficientFunds(amount));
			case ECONOMY_ERROR -> SOUND_CANCEL.playSound(viewer);
		}
	}

	private void onBarter(MenuFlow<TraderFlowSession> flow, Player viewer, TraderFlowSession session) {
		flow.switchTo(TraderFlowSession.PANEL_BARTER);
		playSoundNextTick(viewer, SOUND_OPEN_SUB);
	}

	private void onBuyAmount(MenuFlow<TraderFlowSession> flow, Player viewer, TraderFlowSession session) {
		if (session.selectedEntry.getItem().getMaxStackSize() <= 1) return;
		// QuantitySelectorView is now a panel in the trader flow. Reset picker state so the next entry starts fresh,
		// then pivot — its confirm handler fires TraderBuyRequestEvent + flow.end() directly.
		session.quantityStaged = 1;
		session.quantityMode   = 1;
		flow.switchTo(TraderFlowSession.PANEL_QUANTITY);
		playSoundNextTick(viewer, SOUND_OPEN_SUB);
	}

	private void onCancel(MenuFlow<TraderFlowSession> flow, Player viewer) {
		flow.back();
		playSoundNextTick(viewer, SOUND_CANCEL);
	}

	/**
	 * Defers the sound by one tick so it plays after the panel swap has settled on the client. Playing the sound in the
	 * same tick as a flow transition makes the client render the audio cue and the inventory change together, which the
	 * viewer experiences as a flicker even when the transition itself is a re-render against the same inventory
	 * handle.
	 */
	private void playSoundNextTick(Player player, SoundEffect sound) {
		Bukkit.getScheduler().runTask(plugin, () -> sound.playSound(player));
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
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
