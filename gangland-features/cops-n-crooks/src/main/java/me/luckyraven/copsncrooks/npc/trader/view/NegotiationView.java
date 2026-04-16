package me.luckyraven.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.events.trader.TraderBuyRequestEvent;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.message.TraderMessageContract;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.ShopItemEntry;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@RequiredArgsConstructor
public final class NegotiationView implements BeanLifecycle {

	private static final int SIZE          = 54;
	private static final int SLOT_ITEM     = 22;
	private static final int SLOT_BUY      = 38;
	private static final int SLOT_BARGAIN  = 40;
	private static final int SLOT_TIP      = 42;
	private static final int SLOT_BARTER   = 44;
	private static final int SLOT_TRADE_IN = 46;
	private static final int SLOT_CANCEL   = 49;

	private static final SoundConfiguration              SOUND_BUY      = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundConfiguration              SOUND_OPEN_SUB = vanilla("UI_BUTTON_CLICK", 1.2f);
	private static final SoundConfiguration              SOUND_CANCEL   = vanilla("ENTITY_VILLAGER_NO", 1.0f);
	private final        JavaPlugin                      plugin;
	private final        MoodService                     moodService;
	private final        TraderSettings                  settings;
	private final        TraderMessageContract           messages;
	private final        ShopDisplayResolver             displayResolver;
	private final        Map<Player, NegotiationSession> active         = new WeakHashMap<>();
	// Set-after-construction to avoid a cyclic bean dependency with the sub-views.
	private              BargainView                     bargainView;
	private              TipView                         tipView;
	private              BarterConfirmView               barterView;
	private              TradeInView                     tradeInView;

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

	public void setSubViews(BargainView bargainView, TipView tipView, BarterConfirmView barterView,
	                        TradeInView tradeInView) {
		this.bargainView = bargainView;
		this.tipView     = tipView;
		this.barterView  = barterView;
		this.tradeInView = tradeInView;
	}

	public void open(Player viewer, TraderNpc trader, ShopDefinition definition, ShopItemEntry entry,
	                 TraderTraitDefinition trait, Runnable backToShopView) {
		double basePrice = entry.hasPrice() ? entry.getPrice() : 0D;
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

	public double currentPrice(NegotiationSession session) {
		return session.stagedPrice * session.moodMultiplier;
	}

	public void applyBargainPrice(Player viewer, double newStagedPrice) {
		NegotiationSession session = active.get(viewer);
		if (session == null) return;
		double floor   = session.basePrice * 0.4;
		double ceiling = session.basePrice;
		session.stagedPrice = Math.clamp(ceiling, floor, newStagedPrice);
	}

	public void applyMoodBump(Player viewer) {
		NegotiationSession session = active.get(viewer);
		if (session == null) return;
		session.moodMultiplier = moodService.priceMultiplier(session.trader.getData().getId(), viewer.getUniqueId(),
		                                                     session.trait.profile());
	}

	// ── Rendering ────────────────────────────────────────────────────────

	/**
	 * Invoked from {@code NegotiationSessionListener} when a Bukkit inventory-close event fires. Skips if a sub-view is
	 * pending (so opening bargain/tip/barter doesn't kill the negotiation session). Otherwise drops the session and
	 * runs the "back to shop" callback.
	 */
	public void handleClose(Player viewer, org.bukkit.inventory.Inventory inventory) {
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
		double        price = currentPrice(session);
		ShopItemEntry entry = session.entry;

		ItemStack   previewStack = entry.getItem().clone();
		ItemBuilder preview      = new ItemBuilder(previewStack);
		preview.setDisplayName(displayResolver.cleanDisplayName(previewStack))
		       .setLore("&7Asking: &6$" + NumberUtil.valueFormat(price), "&7Trait: &d" + session.trait.displayName(),
		                "&7Mood: " + moodLabel(session.moodMultiplier));
		session.handler.setItem(SLOT_ITEM, preview, false, (p, inv, b) -> { });

		ItemBuilder buy = new ItemBuilder(material(XMaterial.EMERALD_BLOCK, Material.EMERALD_BLOCK));
		buy.setDisplayName("&a&lBUY").setLore("&7Pay &6$" + NumberUtil.valueFormat(price) + " &7and receive the item.");
		session.handler.setItem(SLOT_BUY, buy, false, (p, inv, b) -> onBuy(p, session));

		if (session.trait.profile().allowsBargaining()) {
			ItemBuilder bargain = new ItemBuilder(material(XMaterial.BOOK, Material.BOOK));
			bargain.setDisplayName("&eBARGAIN")
			       .setLore("&7Offer a lower price.", "&8Rounds used: " + session.bargainRoundsUsed + "/" +
			                                          session.trait.profile().bargainMaxRounds());
			session.handler.setItem(SLOT_BARGAIN, bargain, false, (p, inv, b) -> onBargain(p, session));
		}

		ItemBuilder tip = new ItemBuilder(material(XMaterial.GOLD_NUGGET, Material.GOLD_NUGGET));
		tip.setDisplayName("&6TIP").setLore("&7Raise trader's mood for a better future price.");
		session.handler.setItem(SLOT_TIP, tip, false, (p, inv, b) -> onTip(p, session));

		boolean canBarter = entry.hasBarter() && session.trait.profile().allowsBarter();
		if (canBarter) {
			ItemBuilder barter = new ItemBuilder(material(XMaterial.EMERALD, Material.EMERALD));
			barter.setDisplayName("&bBARTER")
			      .setLore(
						  "&7Trade &b" + entry.getTradeFor().getType().name() + " ×" + entry.getTradeFor().getAmount() +
					      " &7for this item.");
			session.handler.setItem(SLOT_BARTER, barter, false, (p, inv, b) -> onBarter(p, session));
		}

		ItemBuilder tradeIn = new ItemBuilder(material(XMaterial.CHEST, Material.CHEST));
		tradeIn.setDisplayName("&dTRADE-IN").setLore("&7Offset the price with items you already own.");
		session.handler.setItem(SLOT_TRADE_IN, tradeIn, false, (p, inv, b) -> onTradeIn(p, session));

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER));
		cancel.setDisplayName("&cCANCEL").setLore("&7Return to the shop.");
		session.handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> onCancel(p, session));
	}

	private void onBuy(Player viewer, NegotiationSession session) {
		SOUND_BUY.playSound(viewer);
		double finalPrice = currentPrice(session);

		TraderBuyRequestEvent event = new TraderBuyRequestEvent(viewer, session.trader, session.entry, finalPrice);
		Bukkit.getPluginManager().callEvent(event);

		// The buy listener owns user-facing messaging (success and failure both go through the contract).
		if (event.isCancelled()) {
			if (event.isOfferTradeIn()) {
				onTradeIn(viewer, session);
			}
			return;
		}

		active.remove(viewer);
		session.returnHome = false;
		viewer.closeInventory();
	}

	private void onBargain(Player viewer, NegotiationSession session) {
		SOUND_OPEN_SUB.playSound(viewer);
		if (bargainView == null) return;
		session.pendingSubview = true;
		bargainView.open(viewer, session, this::reopenAfterSubview);
	}

	private void onTip(Player viewer, NegotiationSession session) {
		SOUND_OPEN_SUB.playSound(viewer);
		if (tipView == null) return;
		session.pendingSubview = true;
		tipView.open(viewer, session, this::reopenAfterSubview);
	}

	private void onBarter(Player viewer, NegotiationSession session) {
		SOUND_OPEN_SUB.playSound(viewer);
		if (barterView == null) return;
		session.pendingSubview = true;
		barterView.open(viewer, session, this::reopenAfterSubview);
	}

	private void onTradeIn(Player viewer, NegotiationSession session) {
		SOUND_OPEN_SUB.playSound(viewer);
		if (tradeInView == null) return;
		if (session.definition.getSellCategories().isEmpty()) {
			viewer.sendMessage(messages.tradeInNotConfigured());
			return;
		}
		session.pendingSubview = true;
		tradeInView.open(viewer, session, session.definition, currentPrice(session), this::reopenAfterSubview);
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
		if (multiplier < 1.05) return "&fNeutral";
		if (multiplier < 1.25) return "&eWary";
		return "&cHostile";
	}

	// ── Close handler (invoked by the singleton NegotiationSessionListener) ──

	public static final class NegotiationSession {
		@Getter
		final TraderNpc             trader;
		final ShopDefinition        definition;
		@Getter
		final ShopItemEntry         entry;
		@Getter
		final TraderTraitDefinition trait;
		final Runnable              backToShopView;
		@Getter
		final double                basePrice;
		InventoryHandler handler;
		@Getter
		double stagedPrice;
		double moodMultiplier;
		@Getter
		int bargainRoundsUsed = 0;
		boolean pendingSubview = false;
		boolean returnHome     = true;

		NegotiationSession(TraderNpc trader, ShopDefinition definition, ShopItemEntry entry,
		                   TraderTraitDefinition trait, Runnable backToShopView, double basePrice,
		                   double moodMultiplier) {
			this.trader         = trader;
			this.definition     = definition;
			this.entry          = entry;
			this.trait          = trait;
			this.backToShopView = backToShopView;
			this.basePrice      = basePrice;
			this.stagedPrice    = basePrice;
			this.moodMultiplier = moodMultiplier;
		}

		public void incrementBargainRound() {
			bargainRoundsUsed++;
		}
	}

}
