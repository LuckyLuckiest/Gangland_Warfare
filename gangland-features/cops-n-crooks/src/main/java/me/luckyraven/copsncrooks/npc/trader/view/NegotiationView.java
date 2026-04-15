package me.luckyraven.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.events.trader.TraderBuyRequestEvent;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.shop.ShopItemEntry;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.WeakHashMap;

@RequiredArgsConstructor
public final class NegotiationView {

	private static final int SIZE         = 54;
	private static final int SLOT_ITEM    = 22;
	private static final int SLOT_BUY     = 38;
	private static final int SLOT_BARGAIN = 40;
	private static final int SLOT_TIP     = 42;
	private static final int SLOT_BARTER  = 44;
	private static final int SLOT_CANCEL  = 49;

	private static final SoundConfiguration SOUND_BUY      = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundConfiguration SOUND_OPEN_SUB = vanilla("UI_BUTTON_CLICK", 1.2f);
	private static final SoundConfiguration SOUND_CANCEL   = vanilla("ENTITY_VILLAGER_NO", 1.0f);
	private final JavaPlugin          plugin;
	private final MoodService         moodService;
	private final TraderSettings      settings;
	private final ShopDisplayResolver displayResolver;
	private final Map<Player, NegotiationSession> active = new WeakHashMap<>();
	// Set-after-construction to avoid a cyclic bean dependency with the sub-views.
	private BargainView       bargainView;
	private TipView           tipView;
	private BarterConfirmView barterView;

	private static SoundConfiguration vanilla(String name, float pitch) {
		return new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, name, 0.6f, pitch);
	}

	public void setSubViews(BargainView bargainView, TipView tipView, BarterConfirmView barterView) {
		this.bargainView = bargainView;
		this.tipView     = tipView;
		this.barterView  = barterView;
	}

	public void open(Player viewer, TraderNpc trader, ShopItemEntry entry, TraderTraitDefinition trait,
	                 Runnable backToShopView) {
		double basePrice = entry.hasPrice() ? entry.getPrice() : 0D;
		double moodMultiplier = moodService.priceMultiplier(trader.getData().getId(),
		                                                    viewer.getUniqueId(), trait.profile());

		NegotiationSession session = new NegotiationSession(trader, entry, trait, backToShopView,
		                                                    basePrice, moodMultiplier);
		session.handler = new InventoryHandler(plugin, "&8Negotiation — " + trait.displayName(), SIZE, viewer);

		active.put(viewer, session);
		render(session);

		InventoryUtil.fillInventory(session.handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		Bukkit.getPluginManager().registerEvents(new SessionListener(viewer, session), plugin);
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
		session.stagedPrice = Math.max(floor, Math.min(ceiling, newStagedPrice));
	}

	public void applyMoodBump(Player viewer) {
		NegotiationSession session = active.get(viewer);
		if (session == null) return;
		session.moodMultiplier = moodService.priceMultiplier(session.trader.getData().getId(),
		                                                     viewer.getUniqueId(), session.trait.profile());
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void render(NegotiationSession session) {
		double        price = currentPrice(session);
		ShopItemEntry entry = session.entry;

		ItemStack previewStack = entry.getItem().clone();
		ItemBuilder preview = new ItemBuilder(previewStack)
				.setDisplayName(displayResolver.cleanDisplayName(previewStack))
				.setLore("&7Asking: &6$" + format(price),
				         "&7Trait: &d" + session.trait.displayName(),
				         "&7Mood: " + moodLabel(session.moodMultiplier));
		session.handler.setItem(SLOT_ITEM, preview, false, (p, inv, b) -> { });

		ItemBuilder buy = new ItemBuilder(material(XMaterial.EMERALD_BLOCK, Material.EMERALD_BLOCK))
				.setDisplayName("&a&lBUY")
				.setLore("&7Pay &6$" + format(price) + " &7and receive the item.");
		session.handler.setItem(SLOT_BUY, buy, false, (p, inv, b) -> onBuy(p, session));

		if (session.trait.profile().allowsBargaining()) {
			ItemBuilder bargain = new ItemBuilder(material(XMaterial.BOOK, Material.BOOK))
					.setDisplayName("&eBARGAIN")
					.setLore("&7Offer a lower price.",
					         "&8Rounds used: " + session.bargainRoundsUsed
					         + "/" + session.trait.profile().bargainMaxRounds());
			session.handler.setItem(SLOT_BARGAIN, bargain, false, (p, inv, b) -> onBargain(p, session));
		}

		ItemBuilder tip = new ItemBuilder(material(XMaterial.GOLD_NUGGET, Material.GOLD_NUGGET))
				.setDisplayName("&6TIP")
				.setLore("&7Raise trader's mood for a better future price.");
		session.handler.setItem(SLOT_TIP, tip, false, (p, inv, b) -> onTip(p, session));

		boolean canBarter = entry.hasBarter() && session.trait.profile().allowsBarter();
		if (canBarter) {
			ItemBuilder barter = new ItemBuilder(material(XMaterial.EMERALD, Material.EMERALD))
					.setDisplayName("&bBARTER")
					.setLore("&7Trade &b" + entry.getTradeFor().getType().name()
					         + " ×" + entry.getTradeFor().getAmount() + " &7for this item.");
			session.handler.setItem(SLOT_BARTER, barter, false, (p, inv, b) -> onBarter(p, session));
		}

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER))
				.setDisplayName("&cCANCEL")
				.setLore("&7Return to the shop.");
		session.handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> onCancel(p, session));
	}

	// ── Actions ──────────────────────────────────────────────────────────

	private void onBuy(Player viewer, NegotiationSession session) {
		SOUND_BUY.playSound(viewer);
		double finalPrice = currentPrice(session);

		TraderBuyRequestEvent event = new TraderBuyRequestEvent(viewer, session.trader, session.entry, finalPrice);
		Bukkit.getPluginManager().callEvent(event);

		// The buy listener owns user-facing messaging (success and failure both go through the contract).
		if (event.isCancelled()) return;

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

	private void onCancel(Player viewer, NegotiationSession session) {
		SOUND_CANCEL.playSound(viewer);
		viewer.closeInventory();
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private String format(double value) {
		if (value == Math.floor(value) && !Double.isInfinite(value)) return String.valueOf((long) value);
		return String.format("%.2f", value);
	}

	private String moodLabel(double multiplier) {
		if (multiplier <= 0.95) return "&aFriendly";
		if (multiplier < 1.05) return "&fNeutral";
		if (multiplier < 1.25) return "&eWary";
		return "&cHostile";
	}

	// ── Session ──────────────────────────────────────────────────────────

	public static final class NegotiationSession {
		final TraderNpc             trader;
		final ShopItemEntry         entry;
		final TraderTraitDefinition trait;
		final Runnable              backToShopView;
		final double basePrice;
		InventoryHandler handler;
		double  stagedPrice;
		double  moodMultiplier;
		int     bargainRoundsUsed = 0;
		boolean pendingSubview    = false;
		boolean returnHome        = true;

		NegotiationSession(TraderNpc trader, ShopItemEntry entry, TraderTraitDefinition trait,
		                   Runnable backToShopView, double basePrice, double moodMultiplier) {
			this.trader         = trader;
			this.entry          = entry;
			this.trait          = trait;
			this.backToShopView = backToShopView;
			this.basePrice      = basePrice;
			this.stagedPrice    = basePrice;
			this.moodMultiplier = moodMultiplier;
		}

		public TraderNpc getTrader() {
			return trader;
		}

		public ShopItemEntry getEntry() {
			return entry;
		}

		public TraderTraitDefinition getTrait() {
			return trait;
		}

		public double getBasePrice() {
			return basePrice;
		}

		public double getStagedPrice() {
			return stagedPrice;
		}

		public int getBargainRoundsUsed() {
			return bargainRoundsUsed;
		}

		public void incrementBargainRound() {
			bargainRoundsUsed++;
		}
	}

	// ── Close listener ───────────────────────────────────────────────────

	private final class SessionListener implements Listener {
		private final Player             viewer;
		private final NegotiationSession session;

		SessionListener(Player viewer, NegotiationSession session) {
			this.viewer  = viewer;
			this.session = session;
		}

		@EventHandler
		public void onClose(InventoryCloseEvent event) {
			if (event.getPlayer() != viewer) return;
			if (event.getInventory() != session.handler.getInventory()) return;
			if (session.pendingSubview) return;

			active.remove(viewer);
			HandlerList.unregisterAll(this);

			if (session.returnHome && session.backToShopView != null) {
				Bukkit.getScheduler().runTask(plugin, session.backToShopView);
			}
		}
	}

}
