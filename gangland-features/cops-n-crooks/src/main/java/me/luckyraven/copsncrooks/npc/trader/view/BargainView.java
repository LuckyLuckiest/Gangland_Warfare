package me.luckyraven.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.message.TraderMessageContract;
import me.luckyraven.copsncrooks.npc.trader.mood.BargainResult;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
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
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class BargainView {

	private static final int      SIZE        = 27;
	private static final double[] OFFERS      = {0.5, 0.7, 0.8, 0.9, 1.0};
	private static final int[]    OFFER_SLOTS = {11, 12, 13, 14, 15};

	private static final SoundConfiguration SOUND_OFFER  = vanilla("UI_BUTTON_CLICK", 1.3f);
	private static final SoundConfiguration SOUND_ACCEPT = vanilla("ENTITY_VILLAGER_YES", 1.0f);
	private static final SoundConfiguration SOUND_REJECT = vanilla("ENTITY_VILLAGER_NO", 0.8f);
	private final JavaPlugin            plugin;
	private final MoodService           moodService;
	private final TraderMessageContract messages;
	private final TraderSettings        settings;
	private final ShopDisplayResolver   displayResolver;
	private final Map<Player, State> active = new WeakHashMap<>();

	private static SoundConfiguration vanilla(String name, float pitch) {
		return new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, name, 0.6f, pitch);
	}

	public void open(Player viewer, NegotiationView.NegotiationSession parent, Consumer<Player> onClose) {
		State state = new State(parent, onClose);
		state.handler = new InventoryHandler(plugin, "&8Bargain", SIZE, viewer);

		ItemStack previewStack = parent.getEntry().getItem().clone();
		ItemBuilder preview = new ItemBuilder(previewStack)
				.setDisplayName(displayResolver.cleanDisplayName(previewStack))
				.setLore("&7Base: &6$" + format(parent.getBasePrice()),
				         "&7Asking: &6$" + format(parent.getStagedPrice()),
				         "&7Rounds: &f" + parent.getBargainRoundsUsed() + "&7/&f"
				         + parent.getTrait().profile().bargainMaxRounds());
		state.handler.setItem(4, preview, false, (p, inv, b) -> { });

		for (int i = 0; i < OFFERS.length; i++) {
			double  ratio  = OFFERS[i];
			double  value  = parent.getBasePrice() * ratio;
			boolean upward = value >= parent.getStagedPrice();
			ItemBuilder button = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER))
					.setDisplayName("&eOffer &6$" + format(value))
					.setLore("&7(" + (int) (ratio * 100) + "% of base)",
					         upward ? "&8(already below this)" : "&7Click to submit");
			state.handler.setItem(OFFER_SLOTS[i], button, false,
			                      (p, inv, b) -> submitOffer(p, state, ratio));
		}

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER))
				.setDisplayName("&cBack");
		state.handler.setItem(22, cancel, false, (p, inv, b) -> viewer.closeInventory());

		InventoryUtil.fillInventory(state.handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		active.put(viewer, state);
		Bukkit.getPluginManager().registerEvents(new CloseListener(viewer, state), plugin);
		state.handler.open(viewer);
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

		BargainResult result = moodService.evaluateBargain(parent.getTrader().getData().getId(),
		                                                   viewer.getUniqueId(), parent.getTrait().profile(),
		                                                   offerPrice, parent.getBasePrice(),
		                                                   parent.getBargainRoundsUsed());

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

	private void applyPrice(NegotiationView.NegotiationSession parent, double newPrice) {
		double floor   = parent.basePrice * 0.4;
		double ceiling = parent.basePrice;
		parent.stagedPrice = Math.max(floor, Math.min(ceiling, newPrice));
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

	// ── State & listener ─────────────────────────────────────────────────

	private static final class State {
		final NegotiationView.NegotiationSession parent;
		final Consumer<Player>                   onClose;
		InventoryHandler handler;

		State(NegotiationView.NegotiationSession parent, Consumer<Player> onClose) {
			this.parent  = parent;
			this.onClose = onClose;
		}
	}

	private final class CloseListener implements Listener {
		private final Player viewer;
		private final State  state;

		CloseListener(Player viewer, State state) {
			this.viewer = viewer;
			this.state  = state;
		}

		@EventHandler
		public void onClose(InventoryCloseEvent event) {
			if (event.getPlayer() != viewer) return;
			if (event.getInventory() != state.handler.getInventory()) return;

			active.remove(viewer);
			HandlerList.unregisterAll(this);

			if (state.onClose != null) {
				Bukkit.getScheduler().runTask(plugin, () -> state.onClose.accept(viewer));
			}
		}
	}

}
