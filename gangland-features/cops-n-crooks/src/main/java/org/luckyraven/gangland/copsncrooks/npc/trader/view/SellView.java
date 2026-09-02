package org.luckyraven.gangland.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.events.trader.TraderSellRequestEvent;
import org.luckyraven.gangland.copsncrooks.listener.trader.TraderSellSessionListener;
import org.luckyraven.gangland.copsncrooks.npc.trader.config.TraderSettings;
import org.luckyraven.gangland.copsncrooks.npc.trader.mood.MoodService;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.inventory.flow.Panel;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.inventory.util.InventoryUtil;
import org.luckyraven.gangland.item.ItemRefresherRegistry;
import org.luckyraven.gangland.shop.message.ShopDisplayResolver;
import org.luckyraven.gangland.shop.valuation.ItemValuation;
import org.luckyraven.gangland.shop.valuation.SellValuator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Drop-zone sell panel. Players drop items into the left dropzone slots and the valuator produces an offer; confirming
 * sells the items for the offered total.
 *
 * <p>The {@link SellState} is kept in a per-player {@link WeakHashMap} on this panel instance rather than on
 * {@link TraderFlowSession} — the state is large (item dropzone mirror, breakdown lines, mood multiplier) and only
 * meaningful while the viewer is actively in this panel. Entry populates the map in {@link #render}; exit paths (back /
 * confirm / natural close) return un-committed items to the player and clear the entry via the flow's
 * {@link MultiPanelInventory#onEnd onEnd} callback.
 *
 * <p>Bukkit click / drag events still flow through
 * {@link TraderSellSessionListener} which dispatches to {@link #handleClick}/{@link #handleDrag} — those look the
 * viewer up in the per-player state and update the offer display in-place (no {@link MultiPanelInventory#rerender}
 * which would {@code clear()} the dropzone).
 */
@RequiredArgsConstructor
public final class SellView implements Panel<TraderFlowSession>, BeanLifecycle {

	private static final int SIZE         = 54;
	private static final int SLOT_TRAIT   = 7;
	private static final int SLOT_OFFER   = 25;
	private static final int SLOT_MOOD    = 34;
	private static final int SLOT_BACK    = 45;
	private static final int SLOT_CLEAR   = 51;
	private static final int SLOT_CONFIRM = 52;

	private static final int[] ALL_DROPZONE_SLOTS = {
			10, 11, 12, 13, 14,
			19, 20, 21, 22, 23,
			28, 29, 30, 31, 32,
			37, 38, 39, 40, 41
	};

	private static final SoundEffect SOUND_CONFIRM = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundEffect SOUND_CANCEL  = vanilla("ENTITY_VILLAGER_NO", 1.0f);

	private final JavaPlugin            plugin;
	private final MoodService           moodService;
	private final SellValuator          valuator;
	private final ItemRefresherRegistry refresherRegistry;
	private final TraderSettings        settings;
	private final ShopDisplayResolver   displayResolver;

	private final Map<Player, SellState> active = new WeakHashMap<>();

	private static SoundEffect vanilla(String name, float pitch) {
		return new SoundEffect(SoundEffect.SoundType.VANILLA, name, 0.6f, pitch);
	}

	private static boolean contains(int[] arr, int value) {
		for (int v : arr) if (v == value) return true;
		return false;
	}

	@Override
	public int size(TraderFlowSession session) {
		return SIZE;
	}

	@Override
	public String title(TraderFlowSession session) {
		return "&8Sell to&r &8&l[&b&l" + session.trait.displayName() + "&8&l]";
	}

	@Override
	public void render(MultiPanelInventory<TraderFlowSession> host, InventoryHandler handler, Player viewer,
	                   TraderFlowSession session) {
		SellState existing = active.get(viewer);
		SellState state;
		if (existing == null) {
			double mood = moodService.priceMultiplier(session.trader.getData().getId(), viewer.getUniqueId(),
			                                          session.trait.profile());
			// Sell prices are inverse: friendly mood (< 1x on buy) should pay more on sell, so invert for sell usage.
			double sellMood = 2.0 - mood;
			int[]  slots    = dropzoneSlots(settings.getSellMaxOfferSlots());
			state = new SellState(handler, session, slots, sellMood, viewer, host);
			active.put(viewer, state);

			// Register dropzone slots with inventory-api so the click handler doesn't auto-cancel placement clicks.
			for (int slot : slots) handler.setItem(slot, null, true);

			// Natural close / ESC / flow end: return un-committed items. Capture the handler ref at entry so the
			// callback works even after the framework clears `current` during cleanup.
			host.onEnd(s -> {
				SellState st = active.remove(viewer);
				if (st != null && !st.committed) returnItemsToPlayer(viewer, st);
			});
		} else {
			state         = existing;
			state.handler = handler;
		}

		renderChrome(state);
	}

	// ── Listener bridges (invoked by TraderSellSessionListener) ──────────

	public ClickOutcome handleClick(Player viewer, org.bukkit.inventory.Inventory inventory,
	                                org.bukkit.inventory.Inventory clickedInventory, int slot, InventoryAction action,
	                                ItemStack currentItem) {
		SellState state = active.get(viewer);
		if (state == null || state.handler.getInventory() != inventory) return ClickOutcome.PASS;
		org.bukkit.inventory.Inventory top = state.handler.getInventory();

		if (clickedInventory == top) {
			if (!contains(state.dropzoneSlots, slot)) return ClickOutcome.PASS;
			scheduleRecompute(viewer, state);
			return ClickOutcome.ALLOW;
		}

		if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
			if (currentItem == null || currentItem.getType() == Material.AIR) return ClickOutcome.PASS;
			int placed = tryPlaceInDropzone(state, currentItem.clone());
			if (placed > 0) {
				ItemStack remaining = currentItem.clone();
				remaining.setAmount(currentItem.getAmount() - placed);
				ItemStack replacement = remaining.getAmount() > 0 ? remaining : null;
				scheduleRecompute(viewer, state);
				return new ClickOutcome(true, replacement);
			}
			return ClickOutcome.CANCEL_ONLY;
		}
		return ClickOutcome.PASS;
	}

	public boolean handleDrag(Player viewer, org.bukkit.inventory.Inventory inventory,
	                          java.util.Collection<Integer> rawSlots) {
		SellState state = active.get(viewer);
		if (state == null || state.handler.getInventory() != inventory) return false;

		int topSize = state.handler.getInventory().getSize();
		for (int raw : rawSlots) {
			if (raw < topSize && !contains(state.dropzoneSlots, raw)) return true;
		}
		scheduleRecompute(viewer, state);
		return false;
	}

	// ── Lifecycle: return in-flight items on plugin shutdown ──

	@Override
	public void onShutdown() {
		List<Player> viewers = new ArrayList<>(active.keySet());
		for (Player viewer : viewers) {
			SellState state = active.remove(viewer);
			if (state == null) continue;
			state.committed = true; // skip the onEnd return pass
			returnItemsToPlayer(viewer, state);
			try {
				viewer.closeInventory();
			} catch (Exception ignored) { }
		}
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void renderChrome(SellState state) {
		state.handler.getInventory().setItem(SLOT_TRAIT, null);
		state.handler.getInventory().setItem(SLOT_OFFER, null);
		state.handler.getInventory().setItem(SLOT_MOOD, null);
		state.handler.getInventory().setItem(SLOT_BACK, null);
		state.handler.getInventory().setItem(SLOT_CLEAR, null);
		state.handler.getInventory().setItem(SLOT_CONFIRM, null);

		renderTrait(state);
		renderOffer(state);
		renderMood(state);
		renderBack(state);
		renderClear(state);
		renderConfirm(state);

		// Snapshot dropzone contents, fill decorative empty slots, then restore dropzones so the filler doesn't
		// claim them (they must remain truly empty so players can drop items freely).
		ItemStack[] preservedDropzone = new ItemStack[state.dropzoneSlots.length];
		for (int i = 0; i < state.dropzoneSlots.length; i++) {
			preservedDropzone[i] = state.handler.getInventory().getItem(state.dropzoneSlots[i]);
			state.handler.getInventory().setItem(state.dropzoneSlots[i], new ItemStack(Material.BARRIER));
		}

		InventoryUtil.fillInventory(state.handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		for (int i = 0; i < state.dropzoneSlots.length; i++) {
			state.handler.getInventory().setItem(state.dropzoneSlots[i], preservedDropzone[i]);
		}
	}

	private void renderTrait(SellState state) {
		ItemBuilder trait = new ItemBuilder(material(XMaterial.DIAMOND, Material.DIAMOND));
		trait.setDisplayName("&d&lTrait: &d" + state.session.trait.displayName())
		     .setLore("&7This trader's valuation style.", " ", "&8Drop items on the left to get an offer.");
		state.handler.setItem(SLOT_TRAIT, trait, false, (p, inv, b) -> { });
	}

	private void renderMood(SellState state) {
		double      mood = 2.0 - state.sellMoodMultiplier;
		ItemBuilder pane = new ItemBuilder(material(XMaterial.NETHER_STAR, Material.NETHER_STAR));
		pane.setDisplayName("&b&lMood: " + moodLabel(mood))
		    .setLore("&7Sell multiplier:",
		             "&e" + String.format("%.2fx", state.sellMoodMultiplier),
		             " ",
		             "&8Friendlier traders pay closer to base price.");
		state.handler.setItem(SLOT_MOOD, pane, false, (p, inv, b) -> { });
	}

	private String moodLabel(double multiplier) {
		if (multiplier <= 0.95) return "&aFriendly";
		return "&fNeutral";
	}

	private void renderOffer(SellState state) {
		recomputeOffer(state);

		ItemBuilder offer = new ItemBuilder(material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT)).setDisplayName(
				"&6Offer: &e$" + NumberUtil.valueFormat(state.offeredTotal));
		List<String> lore = new ArrayList<>();
		if (state.breakdown.isEmpty()) {
			lore.add("&8Drop items to the left to get an offer.");
		} else {
			int shown = Math.min(state.breakdown.size(), 5);
			for (int i = 0; i < shown; i++) lore.add(state.breakdown.get(i));
			if (state.breakdown.size() > shown) lore.add("&8…and " + (state.breakdown.size() - shown) + " more");
		}
		offer.setLore(lore);
		state.handler.setItem(SLOT_OFFER, offer, false, (p, inv, b) -> { });
	}

	private void renderBack(SellState state) {
		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to menu")
		                                                  .setLore("&7Return your items and go back.");
		state.handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> onBack(p, state));
	}

	private void renderClear(SellState state) {
		ItemBuilder clear = new ItemBuilder(material(XMaterial.HOPPER, Material.HOPPER));
		clear.setDisplayName("&eClear offer").setLore("&7Return all offered items to your inventory.");
		state.handler.setItem(SLOT_CLEAR, clear, false, (p, inv, b) -> onClear(p, state));
	}

	private void renderConfirm(SellState state) {
		boolean hasOffer = state.offeredTotal.signum() > 0;
		ItemStack icon = hasOffer
		                 ? material(XMaterial.LIME_WOOL, Material.GREEN_WOOL)
		                 : material(XMaterial.GRAY_WOOL, Material.GRAY_WOOL);
		ItemBuilder confirm = new ItemBuilder(icon);
		confirm.setDisplayName(hasOffer
		                       ? "&aCONFIRM — $" + NumberUtil.valueFormat(state.offeredTotal)
		                       : "&8Nothing to sell")
		       .setLore("&7Sell for &6$" + NumberUtil.valueFormat(state.offeredTotal) + "&7.");
		state.handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> onConfirm(p, state));
	}

	// ── Offer computation ─────────────────────────────────────────────────

	private void recomputeOffer(SellState state) {
		BigDecimal   total     = BigDecimal.ZERO;
		List<String> breakdown = new ArrayList<>();

		for (int slot : state.dropzoneSlots) {
			ItemStack rawStack = state.handler.getInventory().getItem(slot);
			if (rawStack == null || rawStack.getType() == Material.AIR) continue;

			ItemStack decorated = refresherRegistry.decorate(rawStack, state.viewer);
			ItemValuation valuation = valuator.value(state.session.definition, decorated,
			                                         state.session.trait.profile().sellPriceRatio(),
			                                         state.sellMoodMultiplier);
			String label = displayResolver.cleanDisplayName(decorated);
			if (valuation.hasValue()) {
				BigDecimal lineTotal = valuation.unitPrice().multiply(BigDecimal.valueOf(decorated.getAmount()));
				total = total.add(lineTotal);
				breakdown.add(
						"&7" + label + " x" + decorated.getAmount() + " &8→ &6$" + NumberUtil.valueFormat(lineTotal));
			} else {
				breakdown.add("&8" + label + " x" + decorated.getAmount() + " &8→ &cno offer");
			}
		}

		state.baseOffer    = total;
		state.offeredTotal = total;
		state.breakdown    = breakdown;
	}

	// ── Click actions ────────────────────────────────────────────────────

	private void onBack(Player viewer, SellState state) {
		returnItemsToPlayer(viewer, state);
		state.committed = true;  // suppress the onEnd return pass
		active.remove(viewer);
		host(state).back();
		Bukkit.getScheduler().runTask(plugin, () -> SOUND_CANCEL.playSound(viewer));
	}

	private void onClear(Player viewer, SellState state) {
		returnItemsToPlayer(viewer, state);
		recomputeOffer(state);
		renderChrome(state);
	}

	private void onConfirm(Player viewer, SellState state) {
		if (state.offeredTotal.signum() <= 0) return;

		// Walk the dropzone once, collecting only items the valuator accepted. No-offer items stay put so the return
		// pass hands them back instead of silently consuming them on confirm.
		List<ItemStack> soldItems = new ArrayList<>();
		List<Integer>   soldSlots = new ArrayList<>();
		for (int slot : state.dropzoneSlots) {
			ItemStack rawStack = state.handler.getInventory().getItem(slot);
			if (rawStack == null || rawStack.getType() == Material.AIR) continue;
			ItemStack decorated = refresherRegistry.decorate(rawStack, state.viewer);
			ItemValuation valuation = valuator.value(state.session.definition, decorated,
			                                         state.session.trait.profile().sellPriceRatio(),
			                                         state.sellMoodMultiplier);
			if (!valuation.hasValue()) continue;
			soldItems.add(rawStack.clone());
			soldSlots.add(slot);
		}

		TraderSellRequestEvent event = new TraderSellRequestEvent(viewer, state.session.trader, soldItems,
		                                                          state.offeredTotal);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) return;

		state.committed = true;
		for (int slot : soldSlots) state.handler.getInventory().setItem(slot, null);
		// committed=true skips the onEnd return pass, so hand back the no-offer leftovers explicitly.
		returnItemsToPlayer(viewer, state);
		active.remove(viewer);
		host(state).back();
		Bukkit.getScheduler().runTask(plugin, () -> SOUND_CONFIRM.playSound(viewer));
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private MultiPanelInventory<TraderFlowSession> host(SellState state) {
		// The state captures the handler at entry; we need the host ref for back(). Stored directly on SellState.
		return state.host;
	}

	private void returnItemsToPlayer(Player viewer, SellState state) {
		for (int slot : state.dropzoneSlots) {
			ItemStack stack = state.handler.getInventory().getItem(slot);
			if (stack == null || stack.getType() == Material.AIR) continue;
			Map<Integer, ItemStack> leftover = viewer.getInventory().addItem(stack.clone());
			for (ItemStack drop : leftover.values()) {
				viewer.getWorld().dropItemNaturally(viewer.getLocation(), drop);
			}
			state.handler.getInventory().setItem(slot, null);
		}
	}

	private int[] dropzoneSlots(int cap) {
		int   limit = Math.max(1, Math.min(cap, ALL_DROPZONE_SLOTS.length));
		int[] out   = new int[limit];
		System.arraycopy(ALL_DROPZONE_SLOTS, 0, out, 0, limit);
		return out;
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private int tryPlaceInDropzone(SellState state, ItemStack stack) {
		int remaining  = stack.getAmount();
		int maxPerSlot = stack.getMaxStackSize();

		for (int slot : state.dropzoneSlots) {
			if (remaining <= 0) break;
			ItemStack current = state.handler.getInventory().getItem(slot);
			if (current == null || current.getType() == Material.AIR) {
				ItemStack placed = stack.clone();
				int       amount = Math.min(remaining, maxPerSlot);
				placed.setAmount(amount);
				state.handler.getInventory().setItem(slot, placed);
				remaining -= amount;
			} else if (current.isSimilar(stack) && current.getAmount() < maxPerSlot) {
				int space = maxPerSlot - current.getAmount();
				int add   = Math.min(space, remaining);
				current.setAmount(current.getAmount() + add);
				remaining -= add;
			}
		}
		return stack.getAmount() - remaining;
	}

	private void scheduleRecompute(Player viewer, SellState state) {
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (active.get(viewer) != state) return;
			recomputeOffer(state);
			renderOffer(state);
			renderConfirm(state);
		});
	}

	public static final class SellState {
		final Player                                 viewer;
		final TraderFlowSession                      session;
		final int[]                                  dropzoneSlots;
		final double                                 sellMoodMultiplier;
		final MultiPanelInventory<TraderFlowSession> host;

		InventoryHandler handler;

		@Getter
		BigDecimal baseOffer = BigDecimal.ZERO;
		BigDecimal   offeredTotal = BigDecimal.ZERO;
		List<String> breakdown    = new ArrayList<>();
		boolean      committed    = false;

		SellState(InventoryHandler handler, TraderFlowSession session, int[] dropzoneSlots, double sellMoodMultiplier,
		          Player viewer, MultiPanelInventory<TraderFlowSession> host) {
			this.viewer             = viewer;
			this.session            = session;
			this.handler            = handler;
			this.dropzoneSlots      = dropzoneSlots;
			this.sellMoodMultiplier = sellMoodMultiplier;
			this.host               = host;
		}
	}

	public record ClickOutcome(boolean cancel, ItemStack replacementCurrent, boolean replace) {
		public static final ClickOutcome PASS        = new ClickOutcome(false, null, false);
		public static final ClickOutcome ALLOW       = new ClickOutcome(false, null, false);
		public static final ClickOutcome CANCEL_ONLY = new ClickOutcome(true, null, false);

		public ClickOutcome(boolean cancel, ItemStack replacementCurrent) {
			this(cancel, replacementCurrent, true);
		}
	}

}
