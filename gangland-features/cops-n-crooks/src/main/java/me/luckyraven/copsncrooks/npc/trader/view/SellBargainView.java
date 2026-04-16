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
public final class SellBargainView implements BeanLifecycle {

	private static final int SIZE      = 27;
	private static final int SLOT_INFO = 4;
	private static final int SLOT_BACK = 22;

	private static final SoundConfiguration SOUND_OFFER  = vanilla("UI_BUTTON_CLICK", 1.3f);
	private static final SoundConfiguration SOUND_ACCEPT = vanilla("ENTITY_VILLAGER_YES", 1.0f);
	private static final SoundConfiguration SOUND_REJECT = vanilla("ENTITY_VILLAGER_NO", 0.8f);

	private final JavaPlugin             plugin;
	private final MoodService            moodService;
	private final BargainCooldownService cooldownService;
	private final TraderMessageContract  messages;
	private final TraderSettings         settings;

	private final Map<Player, State> active = new WeakHashMap<>();

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

	public void open(Player viewer, SellView.Session parent, Consumer<Player> onClose) {
		State state = new State(parent, onClose);
		state.handler = new InventoryHandler(plugin, "&8Bargain - ask for more", SIZE, viewer);

		// If the player proposed a price, that's the starting ask. Otherwise the valuator-computed base offer.
		double anchor = Math.max(parent.getBaseOffer(), parent.getPlayerProposedPrice());

		ItemBuilder info = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		info.setDisplayName("&eBase offer: &6$" + NumberUtil.valueFormat(anchor))
		    .setLore("&7Pick a multiplier to ask for more.", "&7Rounds: &f" + parent.getBargainRoundsUsed() + "&7/&f" +
		                                                     parent.getTrait().profile().bargainMaxRounds());
		state.handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });

		List<Double> multipliers = settings.getSellBargainMultipliers();
		int          count       = Math.min(multipliers.size(), 7);
		int[]        slots       = {10, 11, 12, 13, 14, 15, 16};

		for (int i = 0; i < count; i++) {
			double      multiplier = multipliers.get(i);
			double      projected  = anchor * multiplier;
			ItemBuilder button     = new ItemBuilder(material(XMaterial.GOLD_NUGGET, Material.GOLD_NUGGET));

			button.setDisplayName(
						  "&e× " + NumberUtil.valueFormat(multiplier) + " &8→ &6$" + NumberUtil.valueFormat(projected))
			      .setLore("&7Ask for &6$" + NumberUtil.valueFormat(projected) + "&7.", "&8Click to submit");
			state.handler.setItem(slots[i], button, false, (p, inv, b) -> submitOffer(p, state, multiplier));
		}

		ItemBuilder back = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER)).setDisplayName("&cBack");
		state.handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> viewer.closeInventory());

		InventoryUtil.fillInventory(state.handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		active.put(viewer, state);
		state.handler.open(viewer);
	}

	/**
	 * Close-event handler used by the singleton {@code SellBargainCloseListener}. Drops the state and invokes the
	 * parent sell view's reopen callback.
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

	private void submitOffer(Player viewer, State state, double multiplier) {
		SOUND_OFFER.playSound(viewer);

		SellView.Session parent = state.parent;

		// Per-item cooldown uses the first dropped item's material as a representative key — keeps per-session
		// state keyed by the offering's primary material so rapid re-bargaining on the same item is rate-limited.
		UUID     traderId  = parent.getTrader().getData().getId();
		UUID     playerId  = viewer.getUniqueId();
		Material itemKey   = representativeMaterial(parent);
		int      maxRounds = parent.getTrait().profile().bargainMaxRounds();
		int      available = cooldownService.availableRounds(traderId, playerId, itemKey, maxRounds);
		if (available <= 0) {
			SOUND_REJECT.playSound(viewer);
			long seconds = Math.max(1, settings.getBargainCooldownSeconds() / Math.max(1, maxRounds));
			viewer.sendMessage(messages.bargainOnCooldown(seconds));
			viewer.closeInventory();
			return;
		}

		int    cooldownUsed = cooldownService.roundsUsed(traderId, playerId, itemKey, maxRounds);
		double anchor       = Math.max(parent.getBaseOffer(), parent.getPlayerProposedPrice());
		BargainResult result = moodService.evaluateSellBargain(traderId, playerId, parent.getTrait().profile(), anchor,
		                                                       multiplier, cooldownUsed);
		cooldownService.recordBargain(traderId, playerId, itemKey, maxRounds);

		parent.incrementBargainRound();

		switch (result.outcome()) {
			case ACCEPTED -> {
				SOUND_ACCEPT.playSound(viewer);
				viewer.sendMessage(messages.bargainAccepted(result.resolvedPrice()));
				parent.setBargainOverride(result.resolvedPrice());
			}
			case COUNTER_OFFER -> {
				SOUND_OFFER.playSound(viewer);
				viewer.sendMessage(messages.bargainCounterOffer(result.resolvedPrice()));
				parent.setBargainOverride(result.resolvedPrice());
			}
			case REJECTED -> {
				SOUND_REJECT.playSound(viewer);
				viewer.sendMessage(messages.bargainRejected());
			}
			case HARD_REJECTED -> {
				SOUND_REJECT.playSound(viewer);
				viewer.sendMessage(messages.bargainHardRejected());
				parent.traderAngry = true;
			}
		}
		viewer.closeInventory();
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private Material representativeMaterial(SellView.Session parent) {
		for (int slot : parent.dropzoneSlots) {
			ItemStack stack = parent.handler.getInventory().getItem(slot);
			if (stack != null && stack.getType() != Material.AIR) {
				return stack.getType();
			}
		}
		return Material.AIR;
	}

	private static final class State {
		final SellView.Session parent;
		final Consumer<Player> onClose;
		InventoryHandler handler;

		State(SellView.Session parent, Consumer<Player> onClose) {
			this.parent  = parent;
			this.onClose = onClose;
		}
	}

}
