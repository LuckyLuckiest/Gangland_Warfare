package me.luckyraven.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.events.trader.TraderBarterEvent;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.bean.BeanLifecycle;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.flow.MultiPanelInventory;
import me.luckyraven.inventory.flow.Panel;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.valuation.CategoryBarterValuator;
import me.luckyraven.shop.valuation.ItemValuation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Drop-zone barter panel. The player drops items from any of the shop's barter categories; their combined value (via
 * {@link CategoryBarterValuator}) must meet or exceed the negotiated asking price for the swap to confirm. <strong>No
 * economy money is involved at any point</strong>.
 *
 * <p>Same lifecycle shape as {@link SellView}: per-player {@link BarterState} kept in a {@link WeakHashMap} on the
 * panel instance, populated in {@link #render} and cleared via {@link MultiPanelInventory#onEnd}. Back-navigation
 * returns to the negotiation panel via {@link MultiPanelInventory#back()} (the flow pushed negotiation onto the
 * back-stack when it switched here).
 */
@RequiredArgsConstructor
public final class BarterView implements Panel<TraderFlowSession>, BeanLifecycle {

	private static final int SIZE         = 54;
	private static final int SLOT_TRAIT   = 7;
	private static final int SLOT_ASKING  = 16;
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

	private static final SoundConfiguration SOUND_CONFIRM = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundConfiguration SOUND_CLICK   = vanilla("UI_BUTTON_CLICK", 1.0f);

	private final JavaPlugin             plugin;
	private final MoodService            moodService;
	private final CategoryBarterValuator valuator;
	private final ItemRefresherRegistry  refresherRegistry;
	private final ShopDisplayResolver    displayResolver;
	private final TraderSettings         settings;

	private final Map<Player, BarterState> active = new WeakHashMap<>();

	private static SoundConfiguration vanilla(String name, float pitch) {
		return new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, name, 0.6f, pitch);
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
		ItemStack decorated = refresherRegistry.decorate(session.selectedEntry.getItem(), null);
		String    label     = displayResolver.cleanDisplayName(decorated);
		return "&8Barter for " + label;
	}

	@Override
	public void render(MultiPanelInventory<TraderFlowSession> host, InventoryHandler handler, Player viewer,
	                   TraderFlowSession session) {
		BarterState existing = active.get(viewer);
		BarterState state;
		if (existing == null) {
			double mood = moodService.priceMultiplier(session.trader.getData().getId(), viewer.getUniqueId(),
			                                          session.trait.profile());
			double barterMood = 2.0 - mood;
			int[]  slots      = dropzoneSlots(settings.getSellMaxOfferSlots());
			// Asking value = negotiated base × current mood multiplier — matches NegotiationView.currentPrice().
			BigDecimal asking = session.basePrice.multiply(BigDecimal.valueOf(session.moodMultiplier));

			state = new BarterState(handler, session, slots, barterMood, asking, viewer, host);
			active.put(viewer, state);

			for (int slot : slots) handler.setItem(slot, null, true);

			host.onEnd(s -> {
				BarterState st = active.remove(viewer);
				if (st != null && !st.committed) returnItemsToPlayer(viewer, st);
			});
		} else {
			state         = existing;
			state.handler = handler;
		}

		renderChrome(state);
	}

	// ── Listener bridges ─────────────────────────────────────────────────

	public ClickOutcome handleClick(Player viewer, org.bukkit.inventory.Inventory inventory,
	                                org.bukkit.inventory.Inventory clickedInventory, int slot, InventoryAction action,
	                                ItemStack currentItem) {
		BarterState state = active.get(viewer);
		if (state == null || state.handler.getInventory() != inventory) return ClickOutcome.PASS;

		if (clickedInventory == state.handler.getInventory()) {
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
		BarterState state = active.get(viewer);
		if (state == null || state.handler.getInventory() != inventory) return false;

		int topSize = state.handler.getInventory().getSize();
		for (int raw : rawSlots) {
			if (raw < topSize && !contains(state.dropzoneSlots, raw)) return true;
		}
		scheduleRecompute(viewer, state);
		return false;
	}

	// ── Lifecycle ────────────────────────────────────────────────────────

	@Override
	public void onShutdown() {
		List<Player> viewers = new ArrayList<>(active.keySet());
		for (Player viewer : viewers) {
			BarterState state = active.remove(viewer);
			if (state == null) continue;
			state.committed = true;
			returnItemsToPlayer(viewer, state);
			try {
				viewer.closeInventory();
			} catch (Exception ignored) { }
		}
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void renderChrome(BarterState state) {
		state.handler.getInventory().setItem(SLOT_TRAIT, null);
		state.handler.getInventory().setItem(SLOT_ASKING, null);
		state.handler.getInventory().setItem(SLOT_OFFER, null);
		state.handler.getInventory().setItem(SLOT_MOOD, null);
		state.handler.getInventory().setItem(SLOT_BACK, null);
		state.handler.getInventory().setItem(SLOT_CLEAR, null);
		state.handler.getInventory().setItem(SLOT_CONFIRM, null);

		renderTrait(state);
		renderAsking(state);
		renderOffer(state);
		renderMood(state);
		renderBack(state);
		renderClear(state);
		renderConfirm(state);

		// Dropzone preservation trick — keep slots empty during background fill.
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

	private void renderTrait(BarterState state) {
		ItemBuilder trait = new ItemBuilder(material(XMaterial.DIAMOND, Material.DIAMOND));
		trait.setDisplayName("&d&lTrait: &d" + state.session.trait.displayName())
		     .setLore("&7This trader's bargaining style.", " ", "&8Drop items on the left to make an offer.");
		state.handler.setItem(SLOT_TRAIT, trait, false, (p, inv, b) -> { });
	}

	private void renderAsking(BarterState state) {
		ItemStack   decorated = refresherRegistry.decorate(state.session.selectedEntry.getItem(), state.viewer);
		ItemBuilder asking    = new ItemBuilder(decorated.clone());
		asking.setDisplayName("&6&lAsking for &f" + displayResolver.cleanDisplayName(decorated))
		      .setLore("&7Value required:",
		               "&6$" + NumberUtil.valueFormat(state.askingValue),
		               " ",
		               "&8Meet or exceed this to swap.");
		state.handler.setItem(SLOT_ASKING, asking, false, (p, inv, b) -> { });
	}

	private void renderMood(BarterState state) {
		ItemBuilder mood = new ItemBuilder(material(XMaterial.NETHER_STAR, Material.NETHER_STAR));
		mood.setDisplayName("&b&lMood: " + moodLabel(state.barterMoodMultiplier))
		    .setLore("&7Barter multiplier:",
		             "&e" + String.format("%.2fx", state.barterMoodMultiplier),
		             " ",
		             "&8Friendlier traders value your goods higher.");
		state.handler.setItem(SLOT_MOOD, mood, false, (p, inv, b) -> { });
	}

	private void renderOffer(BarterState state) {
		recomputeOffer(state);

		boolean    ready  = state.offeredValue.compareTo(state.askingValue) >= 0;
		BigDecimal needed = state.askingValue.subtract(state.offeredValue).max(BigDecimal.ZERO);
		BigDecimal excess = state.offeredValue.subtract(state.askingValue).max(BigDecimal.ZERO);

		ItemStack icon;
		if (state.offeredValue.signum() <= 0) icon = material(XMaterial.GOLD_NUGGET, Material.GOLD_NUGGET);
		else if (!ready) icon = material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT);
		else icon = material(XMaterial.EMERALD, Material.EMERALD);

		ItemBuilder  offer = new ItemBuilder(icon).setDisplayName("&6&lBARTER OFFER");
		List<String> lore  = new ArrayList<>();
		if (state.offeredValue.signum() <= 0) {
			lore.add("&7Drop barter items on the left.");
		} else if (!ready) {
			lore.add("&7Asking:   &6$" + NumberUtil.valueFormat(state.askingValue));
			lore.add("&7Offered:  &e$" + NumberUtil.valueFormat(state.offeredValue));
			lore.add("&cNeed:     &c$" + NumberUtil.valueFormat(needed) + " more");
		} else {
			lore.add("&7Asking:   &6$" + NumberUtil.valueFormat(state.askingValue));
			lore.add("&aOffered:  &a$" + NumberUtil.valueFormat(state.offeredValue));
			lore.add("&7Status:   &a&lREADY TO TRADE");
			if (excess.signum() > 0) lore.add("&8(overpay $" + NumberUtil.valueFormat(excess) + " forfeited)");
		}
		lore.add(" ");
		if (state.breakdown.isEmpty()) {
			lore.add("&8No items dropped yet.");
		} else {
			int shown = Math.min(state.breakdown.size(), 5);
			for (int i = 0; i < shown; i++) lore.add(state.breakdown.get(i));
			if (state.breakdown.size() > shown) lore.add("&8…and " + (state.breakdown.size() - shown) + " more");
		}
		offer.setLore(lore);
		state.handler.setItem(SLOT_OFFER, offer, false, (p, inv, b) -> { });
	}

	private void renderBack(BarterState state) {
		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to negotiation")
		                                                  .setLore("&7Return your items and go back.");
		state.handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> onBack(p, state));
	}

	private void renderClear(BarterState state) {
		ItemBuilder clear = new ItemBuilder(material(XMaterial.HOPPER, Material.HOPPER)).setDisplayName(
				"&eClear offer");
		state.handler.setItem(SLOT_CLEAR, clear, false, (p, inv, b) -> onClear(p, state));
	}

	private void renderConfirm(BarterState state) {
		boolean ready = state.offeredValue.compareTo(state.askingValue) >= 0 && state.offeredValue.signum() > 0;

		ItemStack icon = ready ? material(XMaterial.LIME_WOOL, Material.GREEN_WOOL)
		                       : material(XMaterial.GRAY_WOOL, Material.GRAY_WOOL);
		String displayName = ready ? "&aCONFIRM &7swap" : "&8Need more value";

		ItemBuilder confirm = new ItemBuilder(icon);
		confirm.setDisplayName(displayName)
		       .setLore(ready ? "&7Hand over your items and receive the trader's." :
		                "&7Drop barter items worth at least &6$" + NumberUtil.valueFormat(state.askingValue) + "&7.");
		state.handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> onConfirm(p, state));
	}

	private void recomputeOffer(BarterState state) {
		BigDecimal   offered   = BigDecimal.ZERO;
		List<String> breakdown = new ArrayList<>();

		for (int slot : state.dropzoneSlots) {
			ItemStack rawStack = state.handler.getInventory().getItem(slot);
			if (rawStack == null || rawStack.getType() == Material.AIR) continue;

			ItemStack decorated = refresherRegistry.decorate(rawStack, state.viewer);
			ItemValuation valuation = valuator.value(state.session.definition, decorated,
			                                         state.session.trait.profile().barterPriceRatio(),
			                                         state.barterMoodMultiplier);
			String label = displayResolver.cleanDisplayName(decorated);
			if (valuation.hasValue()) {
				BigDecimal lineTotal = valuation.unitPrice().multiply(BigDecimal.valueOf(decorated.getAmount()));
				offered = offered.add(lineTotal);
				breakdown.add(
						"&7" + label + " x" + decorated.getAmount() + " &8→ &a$" + NumberUtil.valueFormat(lineTotal));
			} else {
				breakdown.add("&8" + label + " x" + decorated.getAmount() + " &8→ &cnot accepted");
			}
		}

		state.offeredValue = offered;
		state.breakdown    = breakdown;
	}

	private String moodLabel(double barterMoodMultiplier) {
		if (barterMoodMultiplier >= 1.05) return "&aFriendly";
		if (barterMoodMultiplier > 0.95) return "&fNeutral";
		if (barterMoodMultiplier > 0.75) return "&eWary";
		return "&cHostile";
	}

	// ── Click actions ────────────────────────────────────────────────────

	private void onBack(Player viewer, BarterState state) {
		returnItemsToPlayer(viewer, state);
		state.committed = true; // suppress onEnd return pass
		active.remove(viewer);
		state.host.back();
		Bukkit.getScheduler().runTask(plugin, () -> SOUND_CLICK.playSound(viewer));
	}

	private void onClear(Player viewer, BarterState state) {
		returnItemsToPlayer(viewer, state);
		recomputeOffer(state);
		renderChrome(state);
	}

	private void onConfirm(Player viewer, BarterState state) {
		if (state.offeredValue.compareTo(state.askingValue) < 0 || state.offeredValue.signum() <= 0) return;

		List<ItemStack> offered = collectOfferedItems(state);

		TraderBarterEvent event = new TraderBarterEvent(viewer, state.session.trader, state.session.selectedEntry,
		                                                state.askingValue, state.offeredValue, offered);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) return;

		state.committed = true;
		for (int slot : state.dropzoneSlots) state.handler.getInventory().setItem(slot, null);
		active.remove(viewer);
		state.host.back();
		Bukkit.getScheduler().runTask(plugin, () -> SOUND_CONFIRM.playSound(viewer));
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private List<ItemStack> collectOfferedItems(BarterState state) {
		List<ItemStack> items = new ArrayList<>();
		for (int slot : state.dropzoneSlots) {
			ItemStack stack = state.handler.getInventory().getItem(slot);
			if (stack != null && stack.getType() != Material.AIR) items.add(stack.clone());
		}
		return items;
	}

	private void returnItemsToPlayer(Player viewer, BarterState state) {
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
		int   limit = Math.clamp(cap, 1, ALL_DROPZONE_SLOTS.length);
		int[] out   = new int[limit];
		System.arraycopy(ALL_DROPZONE_SLOTS, 0, out, 0, limit);
		return out;
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private int tryPlaceInDropzone(BarterState state, ItemStack stack) {
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

	private void scheduleRecompute(Player viewer, BarterState state) {
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (active.get(viewer) != state) return;
			recomputeOffer(state);
			renderOffer(state);
			renderConfirm(state);
		});
	}

	static final class BarterState {
		final Player                                 viewer;
		final TraderFlowSession                      session;
		final int[]                                  dropzoneSlots;
		final double                                 barterMoodMultiplier;
		final BigDecimal                             askingValue;
		final MultiPanelInventory<TraderFlowSession> host;

		InventoryHandler handler;
		BigDecimal       offeredValue = BigDecimal.ZERO;
		List<String>     breakdown    = new ArrayList<>();
		boolean          committed    = false;

		BarterState(InventoryHandler handler, TraderFlowSession session, int[] dropzoneSlots,
		            double barterMoodMultiplier, BigDecimal askingValue, Player viewer,
		            MultiPanelInventory<TraderFlowSession> host) {
			this.viewer               = viewer;
			this.session              = session;
			this.handler              = handler;
			this.dropzoneSlots        = dropzoneSlots;
			this.barterMoodMultiplier = barterMoodMultiplier;
			this.askingValue          = askingValue;
			this.host                 = host;
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
