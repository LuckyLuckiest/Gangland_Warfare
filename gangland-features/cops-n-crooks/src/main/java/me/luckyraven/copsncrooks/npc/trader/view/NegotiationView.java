package me.luckyraven.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.events.trader.TraderBuyRequestEvent;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.economy.TraderEconomyContract;
import me.luckyraven.copsncrooks.npc.trader.message.TraderMessageContract;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.bean.BeanLifecycle;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.ShopItemEntry;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.view.QuantitySelectorView;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@RequiredArgsConstructor
public final class NegotiationView implements BeanLifecycle {

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
	private final BarterView            barterView;
	private final QuantitySelectorView  quantitySelectorView;

	private final Map<Player, NegotiationSession> active = new WeakHashMap<>();

	private static SoundConfiguration vanilla(String name, float pitch) {
		return new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, name, 0.6f, pitch);
	}

	@Override
	public void onShutdown() {
		// No drop-zone items here — just close every open negotiation so the player isn't left mid-flow.
		List<Player> viewers = new ArrayList<>(active.keySet());
		for (Player viewer : viewers) {
			NegotiationSession session = active.remove(viewer);
			if (session == null) continue;
			session.returnHome = false;
			try {
				viewer.closeInventory();
			} catch (Exception ignored) {
			}
		}
	}

	public void open(Player viewer, TraderNpc trader, ShopDefinition definition, ShopItemEntry entry,
	                 TraderTraitDefinition trait, Runnable backToShopView) {
		BigDecimal basePrice = entry.hasPrice() ? entry.getPrice() : BigDecimal.ZERO;
		double moodMultiplier = moodService.priceMultiplier(trader.getData().getId(), viewer.getUniqueId(),
		                                                    trait.profile());

		NegotiationSession session = new NegotiationSession(trader, definition, entry, trait, backToShopView, basePrice,
		                                                    moodMultiplier);
		session.handler = new InventoryHandler(plugin, "&8Negotiation&r &8&l[&b&l" + trait.displayName() + "&8&l]",
		                                       SIZE, viewer);

		active.put(viewer, session);
		render(session);

		InventoryUtil.fillInventory(session.handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		session.handler.open(viewer);
	}

	public void reopenAfterSubview(Player viewer) {
		NegotiationSession session = active.get(viewer);
		if (session == null) return;
		session.pendingSubview = false;
		render(session);
		session.handler.open(viewer);
	}

	public BigDecimal currentPrice(NegotiationSession session) {
		return session.basePrice.multiply(BigDecimal.valueOf(session.moodMultiplier));
	}

	// ── Rendering ────────────────────────────────────────────────────────

	/**
	 * Invoked from {@code NegotiationSessionListener} when a Bukkit inventory-close event fires. Skips if a sub-view is
	 * pending (so opening barter doesn't kill the negotiation session). Otherwise drops the session and runs the "back
	 * to shop" callback.
	 */
	public void handleClose(Player viewer, Inventory inventory) {
		NegotiationSession session = active.get(viewer);
		if (session == null) return;
		if (session.handler.getInventory() != inventory) return;
		if (session.pendingSubview) return;

		active.remove(viewer);

		if (session.returnHome && session.backToShopView != null) {
			Bukkit.getScheduler().runTask(plugin, session.backToShopView);
		}
	}

	// ── Actions ──────────────────────────────────────────────────────────

	private void render(NegotiationSession session) {
		BigDecimal    price = currentPrice(session);
		ShopItemEntry entry = session.entry;

		ItemStack   previewStack = entry.getItem().clone();
		ItemBuilder preview      = new ItemBuilder(previewStack);
		preview.setDisplayName(displayResolver.cleanDisplayName(previewStack))
		       .setLore("&7Asking: &6$" + NumberUtil.valueFormat(price), "&7Trait: &d" + session.trait.displayName(),
		                "&7Mood: " + moodLabel(session.moodMultiplier));
		session.handler.setItem(SLOT_ITEM, preview, false, (p, inv, b) -> { });
		// Clear the 8 slots around the preview before recolouring — InventoryUtil.aroundSlot skips any non-null slot,
		// so leftover panes from a previous render would otherwise freeze the ring at the old mood colour.
		for (int ring : SLOT_MOOD_RING) {
			session.handler.getInventory().setItem(ring, null);
		}
		InventoryUtil.aroundSlot(session.handler, SLOT_ITEM, moodRingMaterial(session.moodMultiplier));

		int         templateAmount = Math.max(1, entry.getItem().getAmount());
		ItemBuilder buy            = new ItemBuilder(material(XMaterial.EMERALD_BLOCK, Material.EMERALD_BLOCK));
		buy.setDisplayName("&a&lBUY").setLore("&7Pay &6$" + NumberUtil.valueFormat(price) + " &7and receive &f" +
		                                      templateAmount + " &7items.");
		session.handler.setItem(SLOT_BUY, buy, false, (p, inv, b) -> onBuy(p, session));

		if (entry.getItem().getMaxStackSize() > 1) {
			int         itemsPerCopy = Math.max(1, entry.getItem().getAmount());
			ItemBuilder bulk         = new ItemBuilder(material(XMaterial.CHEST, Material.CHEST));
			bulk.setDisplayName("&a&lBUY AMOUNT")
			    .setLore("&7Choose how many copies to buy.",
			             "&7Per copy: &f" + itemsPerCopy + " items &7for &6$" + NumberUtil.valueFormat(price) + "&7.");
			session.handler.setItem(SLOT_BUY_AMOUNT, bulk, false, (p, inv, b) -> onBuyAmount(p, session));
		}

		BigDecimal  tipAmount = settings.getTipAmount();
		ItemBuilder tip       = new ItemBuilder(material(XMaterial.GOLD_NUGGET, Material.GOLD_NUGGET));
		tip.setDisplayName("&6TIP &e$" + NumberUtil.valueFormat(tipAmount))
		   .setLore("&7Raise trader's mood for a better future price.");
		session.handler.setItem(SLOT_TIP, tip, false, (p, inv, b) -> onTip(p, session, tipAmount));

		boolean canBarter = !session.definition.getBarterCategories().isEmpty() &&
		                    session.trait.profile().allowsBarter();
		if (canBarter) {
			ItemBuilder barter = new ItemBuilder(material(XMaterial.EMERALD, Material.EMERALD));
			barter.setDisplayName("&bBARTER")
			      .setLore("&7Swap items of equal value for this item.", "&7No money changes hands.");
			session.handler.setItem(SLOT_BARTER, barter, false, (p, inv, b) -> onBarter(p, session));
		}

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.ARROW, Material.ARROW));
		cancel.setDisplayName("&cCANCEL").setLore("&7Return to the shop.");
		session.handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> onCancel(p, session));
	}

	private void onBuy(Player viewer, NegotiationSession session) {
		SOUND_BUY.playSound(viewer);
		BigDecimal finalPrice = currentPrice(session);

		TraderBuyRequestEvent event = new TraderBuyRequestEvent(viewer, session.trader, session.entry, finalPrice);
		Bukkit.getPluginManager().callEvent(event);

		// The buy listener owns user-facing messaging (success and failure both go through the contract).
		if (event.isCancelled()) {
			return;
		}

		active.remove(viewer);
		session.returnHome = false;
		viewer.closeInventory();
	}

	private void onTip(Player viewer, NegotiationSession session, BigDecimal amount) {
		TraderEconomyContract.TipResult result = economy.tryTip(viewer, amount);
		switch (result) {
			case SUCCESS -> {
				SOUND_TIP.playSound(viewer);
				moodService.recordTip(session.trader.getData().getId(), viewer.getUniqueId(), amount,
				                      session.trait.profile());
				viewer.sendMessage(messages.tipSuccess(amount));
				session.moodMultiplier = moodService.priceMultiplier(session.trader.getData().getId(),
				                                                     viewer.getUniqueId(), session.trait.profile());
				render(session);
			}
			case INSUFFICIENT_FUNDS -> viewer.sendMessage(messages.tipInsufficientFunds(amount));
			case ECONOMY_ERROR -> SOUND_CANCEL.playSound(viewer);
		}
	}

	private void onBarter(Player viewer, NegotiationSession session) {
		SOUND_OPEN_SUB.playSound(viewer);
		if (barterView == null) return;
		session.pendingSubview = true;
		barterView.open(viewer, session, session.definition, currentPrice(session), this::reopenAfterSubview);
	}

	private void onBuyAmount(Player viewer, NegotiationSession session) {
		SOUND_OPEN_SUB.playSound(viewer);
		if (quantitySelectorView == null) return;
		if (session.entry.getItem().getMaxStackSize() <= 1) return;

		BigDecimal unitPrice = currentPrice(session);
		int        maxCopies = 999;

		session.pendingSubview = true;
		String title = "&8Buy Amount&r &8&l[&b&l" + session.trait.displayName() + "&8&l]";
		quantitySelectorView.open(viewer, title, session.entry.getItem(), unitPrice, 1, maxCopies,
		                          copies -> onBuyAmountConfirmed(viewer, session, copies),
		                          () -> reopenAfterSubview(viewer));
	}

	private void onBuyAmountConfirmed(Player viewer, NegotiationSession session, int copies) {
		SOUND_BUY.playSound(viewer);
		BigDecimal total = currentPrice(session).multiply(BigDecimal.valueOf(copies));

		TraderBuyRequestEvent event = new TraderBuyRequestEvent(viewer, session.trader, session.entry, total, copies);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			return;
		}

		active.remove(viewer);
		session.returnHome = false;
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private void onCancel(Player viewer, NegotiationSession session) {
		SOUND_CANCEL.playSound(viewer);
		viewer.closeInventory();
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	// ── Session ──────────────────────────────────────────────────────────

	private String moodLabel(double multiplier) {
		if (multiplier <= 0.95) return "&aFriendly";
		return "&fNeutral";
	}

	private Material moodRingMaterial(double multiplier) {
		if (multiplier <= 0.95) return Material.LIME_STAINED_GLASS_PANE;
		return Material.WHITE_STAINED_GLASS_PANE;
	}

	// ── Close handler (invoked by the singleton NegotiationSessionListener) ──

	public static final class NegotiationSession {
		@Getter
		final TraderNpc             trader;
		@Getter
		final ShopDefinition        definition;
		@Getter
		final ShopItemEntry         entry;
		@Getter
		final TraderTraitDefinition trait;
		final Runnable              backToShopView;
		@Getter
		final BigDecimal            basePrice;
		InventoryHandler handler;
		double           moodMultiplier;
		boolean          pendingSubview = false;
		boolean          returnHome     = true;

		NegotiationSession(TraderNpc trader, ShopDefinition definition, ShopItemEntry entry,
		                   TraderTraitDefinition trait, Runnable backToShopView, BigDecimal basePrice,
		                   double moodMultiplier) {
			this.trader         = trader;
			this.definition     = definition;
			this.entry          = entry;
			this.trait          = trait;
			this.backToShopView = backToShopView;
			this.basePrice      = basePrice;
			this.moodMultiplier = moodMultiplier;
		}
	}

}
