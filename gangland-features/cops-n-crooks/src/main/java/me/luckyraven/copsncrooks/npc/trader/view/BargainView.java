package me.luckyraven.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.message.TraderMessageContract;
import me.luckyraven.copsncrooks.npc.trader.mood.BargainCooldownService;
import me.luckyraven.copsncrooks.npc.trader.mood.BargainResult;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
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

import java.util.*;
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class BargainView implements BeanLifecycle {

	private static final int      SIZE        = 27;
	private static final double[] OFFERS      = {0.5, 0.7, 0.8, 0.9, 1.0};
	private static final int[]    OFFER_SLOTS = {11, 12, 13, 14, 15};

	private static final SoundConfiguration     SOUND_OFFER  = vanilla("UI_BUTTON_CLICK", 1.3f);
	private static final SoundConfiguration     SOUND_ACCEPT = vanilla("ENTITY_VILLAGER_YES", 1.0f);
	private static final SoundConfiguration     SOUND_REJECT = vanilla("ENTITY_VILLAGER_NO", 0.8f);
	private final        JavaPlugin             plugin;
	private final        MoodService            moodService;
	private final        BargainCooldownService cooldownService;
	private final        TraderMessageContract  messages;
	private final        TraderSettings         settings;
	private final        ShopDisplayResolver    displayResolver;
	private final        Map<Player, State>     active       = new WeakHashMap<>();

	private static SoundConfiguration vanilla(String name, float pitch) {
		return new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, name, 0.6f, pitch);
	}

	@Override
	public void onShutdown() {
		List<Player> viewers = new ArrayList<>(active.keySet());
		for (Player viewer : viewers) {
			active.remove(viewer);
			try {
				viewer.closeInventory();
			} catch (Exception ignored) {
			}
		}
	}

	public void open(Player viewer, NegotiationView.NegotiationSession parent, Consumer<Player> onClose) {
		State state = new State(parent, onClose);
		state.handler = new InventoryHandler(plugin, "&8&lBargain", SIZE, viewer);

		ItemStack   previewStack = parent.getEntry().getItem().clone();
		ItemBuilder preview      = new ItemBuilder(previewStack);

		preview.setDisplayName(displayResolver.cleanDisplayName(previewStack))
		       .setLore("&7Base: &6$" + NumberUtil.valueFormat(parent.getBasePrice()),
		                "&7Asking: &6$" + NumberUtil.valueFormat(parent.getStagedPrice()),
		                "&7Rounds: &f" + parent.getBargainRoundsUsed() + "&7/&f" +
		                parent.getTrait().profile().bargainMaxRounds());
		state.handler.setItem(4, preview, false, (p, inv, b) -> { });

		for (int i = 0; i < OFFERS.length; i++) {
			double      ratio  = OFFERS[i];
			double      value  = parent.getBasePrice() * ratio;
			boolean     upward = value >= parent.getStagedPrice();
			ItemBuilder button = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));

			button.setDisplayName("&eOffer &6$" + NumberUtil.valueFormat(value))
			      .setLore("&7(" + (int) (ratio * 100) + "% of base)",
			               upward ? "&8(already below this)" : "&7Click to submit");
			state.handler.setItem(OFFER_SLOTS[i], button, false, (p, inv, b) -> submitOffer(p, state, ratio));
		}

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER)).setDisplayName("&cBack");
		state.handler.setItem(22, cancel, false, (p, inv, b) -> viewer.closeInventory());

		InventoryUtil.fillInventory(state.handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		active.put(viewer, state);
		state.handler.open(viewer);
	}

	/**
	 * Close-event handler used by the singleton {@code BargainCloseListener} — dispatched by the listener for the
	 * viewer whose inventory just closed. Drops the state and invokes the parent view's reopen callback.
	 */
	public void handleClose(Player viewer, org.bukkit.inventory.Inventory inventory) {
		State state = active.get(viewer);
		if (state == null) return;
		if (state.handler.getInventory() != inventory) return;

		active.remove(viewer);

		if (state.onClose != null) {
			Bukkit.getScheduler().runTask(plugin, () -> state.onClose.accept(viewer));
		}
	}

	private void submitOffer(Player viewer, State state, double ratio) {
		SOUND_OFFER.playSound(viewer);

		NegotiationView.NegotiationSession parent     = state.parent;
		double                             offerPrice = parent.getBasePrice() * ratio;

		// Can't bargain upward — ignore an offer that's at or above what we've already negotiated to.
		if (offerPrice >= parent.getStagedPrice()) {
			SOUND_REJECT.playSound(viewer);
			viewer.sendMessage(messages.bargainAlreadyBelow());
			viewer.closeInventory();
			return;
		}

		// Per-item cooldown gate — if the player has spent all bargain rounds on this item, make them wait.
		UUID     traderId  = parent.getTrader().getData().getId();
		UUID     playerId  = viewer.getUniqueId();
		Material itemType  = parent.getEntry().getItem().getType();
		int      maxRounds = parent.getTrait().profile().bargainMaxRounds();
		int      available = cooldownService.availableRounds(traderId, playerId, itemType, maxRounds);
		if (available <= 0) {
			SOUND_REJECT.playSound(viewer);
			long seconds = Math.max(1, settings.getBargainCooldownSeconds() / Math.max(1, maxRounds));
			viewer.sendMessage(messages.bargainOnCooldown(seconds));
			viewer.closeInventory();
			return;
		}

		int cooldownUsed = cooldownService.roundsUsed(traderId, playerId, itemType, maxRounds);
		BargainResult result = moodService.evaluateBargain(traderId, playerId, parent.getTrait().profile(), offerPrice,
		                                                   parent.getBasePrice(), cooldownUsed);

		cooldownService.recordBargain(traderId, playerId, itemType, maxRounds);
		parent.incrementBargainRound();

		switch (result.outcome()) {
			case ACCEPTED -> {
				SOUND_ACCEPT.playSound(viewer);
				viewer.sendMessage(messages.bargainAccepted(result.resolvedPrice()));
				applyPrice(parent, result.resolvedPrice());
			}
			case COUNTER_OFFER -> {
				SOUND_OFFER.playSound(viewer);
				viewer.sendMessage(messages.bargainCounterOffer(result.resolvedPrice()));
				applyPrice(parent, result.resolvedPrice());
			}
			case REJECTED -> {
				SOUND_REJECT.playSound(viewer);
				viewer.sendMessage(messages.bargainRejected());
			}
			case HARD_REJECTED -> {
				SOUND_REJECT.playSound(viewer);
				viewer.sendMessage(messages.bargainHardRejected());
			}
		}
		viewer.closeInventory();
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private void applyPrice(NegotiationView.NegotiationSession parent, double newPrice) {
		double floor   = parent.basePrice * 0.4;
		double ceiling = parent.basePrice;
		parent.stagedPrice = Math.clamp(ceiling, floor, newPrice);
	}


	// ── State & listener ─────────────────────────────────────────────────

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	static final class State {
		final NegotiationView.NegotiationSession parent;
		final Consumer<Player>                   onClose;
		InventoryHandler handler;

		State(NegotiationView.NegotiationSession parent, Consumer<Player> onClose) {
			this.parent  = parent;
			this.onClose = onClose;
		}
	}

}
