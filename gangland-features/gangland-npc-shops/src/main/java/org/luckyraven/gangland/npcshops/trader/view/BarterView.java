package org.luckyraven.gangland.npcshops.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.npcshops.events.trader.TraderBarterEvent;
import org.luckyraven.gangland.npcshops.trader.config.TraderSettings;
import org.luckyraven.gangland.npcshops.trader.mood.MoodService;
import org.luckyraven.keystone.inventory.chest.ChestMenu;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.keystone.item.ItemRefresherRegistry;
import org.luckyraven.gangland.shop.message.ShopDisplayResolver;
import org.luckyraven.gangland.shop.valuation.CategoryBarterValuator;
import org.luckyraven.gangland.shop.valuation.ItemValuation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Drop-zone barter panel. The player drops items from any of the shop's barter categories; their combined value (via
 * {@link CategoryBarterValuator}) must meet or exceed the negotiated asking price for the swap to confirm. <strong>No
 * economy money is involved at any point</strong>.
 *
 * <p>WS2 G4 re-point (§0d): the 20 dropzone slots are {@link DropzoneSlotComponent}s — interactive, and each an
 * {@code ItemHoldingComponent} reading/clearing straight from the live {@link ChestMenu#bukkitInventory()} — so
 * Keystone's own item-return contract (Escape, disconnect, reload, a differently-sized panel switch) returns
 * whatever a player dropped automatically. The old {@code MultiPanelInventory#onEnd} per-render registration has no
 * equivalent on the new immutable-at-build {@link MenuFlow} (its {@code onEnd} is set once, at flow construction);
 * {@link TraderFlow} instead wires one flow-wide {@code onEnd} that calls {@link #onFlowEnd(Player)} unconditionally
 * (a no-op unless this view is the one that was active), which now only needs to clear the per-player state entry —
 * the physical item return already happened via the contract above before {@code onEnd} even fires.
 *
 * <p>Same lifecycle shape as {@link SellView}: per-player {@link BarterState} kept in a {@link WeakHashMap} on the
 * panel instance, populated in {@link #render}. Back-navigation returns to the negotiation panel via
 * {@link MenuFlow#back()} (the flow pushed negotiation onto the back-stack when it switched here).
 */
@RequiredArgsConstructor
public final class BarterView implements Panel<TraderFlowSession>, BeanLifecycle {

	private static final int ROWS         = 6;
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

	private static final SoundEffect SOUND_CONFIRM = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundEffect SOUND_CLICK   = vanilla("UI_BUTTON_CLICK", 1.0f);

	private final JavaPlugin             plugin;
	private final MoodService            moodService;
	private final CategoryBarterValuator valuator;
	private final ItemRefresherRegistry  refresherRegistry;
	private final ShopDisplayResolver    displayResolver;
	private final TraderSettings         settings;

	private final Map<Player, BarterState> active = new WeakHashMap<>();

	private static SoundEffect vanilla(String name, float pitch) {
		return new SoundEffect(SoundEffect.SoundType.VANILLA, name, 0.6f, pitch);
	}

	private static boolean contains(int[] arr, int value) {
		for (int v : arr) if (v == value) return true;
		return false;
	}

	@Override
	public int rows(TraderFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(TraderFlowSession session) {
		ItemStack decorated = refresherRegistry.decorate(session.selectedEntry.getItem(), null);
		String    label     = displayResolver.cleanDisplayName(decorated);
		return "&8Barter for " + label;
	}

	@Override
	public void render(MenuFlow<TraderFlowSession> flow, ChestMenuBuilder builder, TraderFlowSession session) {
		Player      viewer   = flow.viewer();
		BarterState existing = active.get(viewer);
		BarterState state;
		if (existing == null) {
			double mood = moodService.priceMultiplier(session.trader.getData().getId(), viewer.getUniqueId(),
			                                          session.trait.profile());
			double barterMood = 2.0 - mood;
			int[]  slots      = dropzoneSlots(settings.getSellMaxOfferSlots());
			// Asking value = negotiated base × current mood multiplier — matches NegotiationView.currentPrice().
			BigDecimal asking = session.basePrice.multiply(BigDecimal.valueOf(session.moodMultiplier));

			state = new BarterState(session, slots, barterMood, asking, viewer, flow);
			active.put(viewer, state);
		} else {
			state = existing;
		}

		renderChrome(builder, state);
	}

	/** Wired by {@link TraderFlow} as one flow-wide {@code onEnd} callback (unconditional, harmless when this view
	 *  was never entered) — clears the per-player state entry. The physical dropzone items are already returned by
	 *  the time this fires, via {@link DropzoneSlotComponent}'s {@code ItemHoldingComponent} contract. */
	public void onFlowEnd(Player viewer) {
		active.remove(viewer);
	}

	// ── Listener bridges ─────────────────────────────────────────────────

	public ClickOutcome handleClick(Player viewer, Inventory inventory, Inventory clickedInventory, int slot,
	                                InventoryAction action, ItemStack currentItem) {
		BarterState state = active.get(viewer);
		Inventory   top   = state == null ? null : state.inventory();
		if (state == null || top == null || top != inventory) return ClickOutcome.PASS;

		if (clickedInventory == top) {
			if (!contains(state.dropzoneSlots, slot)) return ClickOutcome.PASS;
			scheduleRecompute(viewer, state);
			return ClickOutcome.ALLOW;
		}

		if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
			if (currentItem == null || currentItem.getType() == Material.AIR) return ClickOutcome.PASS;
			int placed = tryPlaceInDropzone(state, top, currentItem.clone());
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

	public boolean handleDrag(Player viewer, Inventory inventory, java.util.Collection<Integer> rawSlots) {
		BarterState state = active.get(viewer);
		Inventory   top   = state == null ? null : state.inventory();
		if (state == null || top == null || top != inventory) return false;

		int topSize = top.getSize();
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
			returnItemsToPlayer(viewer, state);
			try {
				viewer.closeInventory();
			} catch (Exception ignored) { }
		}
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void renderChrome(ChestMenuBuilder builder, BarterState state) {
		renderTrait(builder, state);
		renderAsking(builder, state);
		renderOffer(builder, state);
		renderMood(builder, state);
		renderBack(builder, state);
		renderClear(builder, state);
		renderConfirm(builder, state);

		for (int slot : state.dropzoneSlots) {
			// The builder's own permanent interactive floor (N1) — belt-and-suspenders alongside
			// DropzoneSlotComponent's own view.interactive(true): if the component ever threw before that line ran,
			// this floor still keeps the slot from being cleared/click-cancelled.
			builder.interactive(slot);
			builder.slot(slot, new DropzoneSlotComponent(slot));
		}

		builder.fill(FillComponent.of(materialOf(settings.getInventoryFillItem())).name(settings.getInventoryFillName()));
	}

	private void renderTrait(ChestMenuBuilder builder, BarterState state) {
		ItemBuilder trait = new ItemBuilder(material(XMaterial.DIAMOND, Material.DIAMOND));
		trait.setDisplayName("&d&lTrait: &d" + state.session.trait.displayName())
		     .setLore("&7This trader's bargaining style.", " ", "&8Drop items on the left to make an offer.");
		builder.slot(SLOT_TRAIT, ItemComponent.of(trait));
	}

	private void renderAsking(ChestMenuBuilder builder, BarterState state) {
		ItemStack   decorated = refresherRegistry.decorate(state.session.selectedEntry.getItem(), state.viewer);
		ItemBuilder asking    = new ItemBuilder(decorated.clone());
		asking.setDisplayName("&6&lAsking for &f" + displayResolver.cleanDisplayName(decorated))
		      .setLore("&7Value required:",
		               "&6$" + NumberUtil.valueFormat(state.askingValue),
		               " ",
		               "&8Meet or exceed this to swap.");
		builder.slot(SLOT_ASKING, ItemComponent.of(asking));
	}

	private void renderMood(ChestMenuBuilder builder, BarterState state) {
		ItemBuilder mood = new ItemBuilder(material(XMaterial.NETHER_STAR, Material.NETHER_STAR));
		mood.setDisplayName("&b&lMood: " + moodLabel(state.barterMoodMultiplier))
		    .setLore("&7Barter multiplier:",
		             "&e" + String.format("%.2fx", state.barterMoodMultiplier),
		             " ",
		             "&8Friendlier traders value your goods higher.");
		builder.slot(SLOT_MOOD, ItemComponent.of(mood));
	}

	private void renderOffer(ChestMenuBuilder builder, BarterState state) {
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
		builder.slot(SLOT_OFFER, ItemComponent.of(offer));
	}

	private void renderBack(ChestMenuBuilder builder, BarterState state) {
		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to negotiation")
		                                                  .setLore("&7Return your items and go back.");
		builder.slot(SLOT_BACK, ItemComponent.of(back).onAnyClick(ctx -> onBack(ctx.player(), state)));
	}

	private void renderClear(ChestMenuBuilder builder, BarterState state) {
		ItemBuilder clear = new ItemBuilder(material(XMaterial.HOPPER, Material.HOPPER)).setDisplayName(
				"&eClear offer");
		builder.slot(SLOT_CLEAR, ItemComponent.of(clear).onAnyClick(ctx -> onClear(ctx.player(), state)));
	}

	private void renderConfirm(ChestMenuBuilder builder, BarterState state) {
		boolean ready = state.offeredValue.compareTo(state.askingValue) >= 0 && state.offeredValue.signum() > 0;

		ItemStack icon = ready ? material(XMaterial.LIME_WOOL, Material.GREEN_WOOL)
		                       : material(XMaterial.GRAY_WOOL, Material.GRAY_WOOL);
		String displayName = ready ? "&aCONFIRM &7swap" : "&8Need more value";

		ItemBuilder confirm = new ItemBuilder(icon);
		confirm.setDisplayName(displayName)
		       .setLore(ready ? "&7Hand over your items and receive the trader's." :
		                "&7Drop barter items worth at least &6$" + NumberUtil.valueFormat(state.askingValue) + "&7.");
		builder.slot(SLOT_CONFIRM, ItemComponent.of(confirm).onAnyClick(ctx -> onConfirm(ctx.player(), state)));
	}

	private void recomputeOffer(BarterState state) {
		Inventory inv = state.inventory();
		if (inv == null) return;

		BigDecimal   offered   = BigDecimal.ZERO;
		List<String> breakdown = new ArrayList<>();

		for (int slot : state.dropzoneSlots) {
			ItemStack rawStack = inv.getItem(slot);
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
		active.remove(viewer);
		state.flow.back();
		Bukkit.getScheduler().runTask(plugin, () -> SOUND_CLICK.playSound(viewer));
	}

	private void onClear(Player viewer, BarterState state) {
		returnItemsToPlayer(viewer, state);
		state.flow.rerender();
	}

	private void onConfirm(Player viewer, BarterState state) {
		if (state.offeredValue.compareTo(state.askingValue) < 0 || state.offeredValue.signum() <= 0) return;

		Inventory inv = state.inventory();
		if (inv == null) return;

		// Only the stacks the valuator accepted are part of the swap. Stacks it rejected ("not accepted") stay in the
		// dropzone so the explicit return pass below hands them back instead of destroying them.
		List<Integer>   consumedSlots = acceptedSlots(state, inv);
		List<ItemStack> offered       = collectOfferedItems(inv, consumedSlots);

		TraderBarterEvent event = new TraderBarterEvent(viewer, state.session.trader, state.session.selectedEntry,
		                                                state.askingValue, state.offeredValue, offered);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) return;

		for (int slot : consumedSlots) inv.setItem(slot, null);
		returnItemsToPlayer(viewer, state);
		active.remove(viewer);
		state.flow.back();
		Bukkit.getScheduler().runTask(plugin, () -> SOUND_CONFIRM.playSound(viewer));
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	/**
	 * The dropzone slots holding a stack the barter valuator gave a value to - i.e. the stacks that actually pay for
	 * the swap. Mirrors {@code SellView#onConfirm}: everything else is left alone so it can be returned.
	 */
	private List<Integer> acceptedSlots(BarterState state, Inventory inventory) {
		return acceptedSlots(state.dropzoneSlots, inventory, rawStack -> {
			ItemStack decorated = refresherRegistry.decorate(rawStack, state.viewer);
			return valuator.value(state.session.definition, decorated,
			                      state.session.trait.profile().barterPriceRatio(),
			                      state.barterMoodMultiplier).hasValue();
		});
	}

	/**
	 * Walks {@code dropzoneSlots} and keeps only the slots whose stack {@code accepted} says the trader values. Split
	 * out as a static so the "rejected stacks are never consumed" rule is testable without a live inventory view.
	 */
	static List<Integer> acceptedSlots(int[] dropzoneSlots, Inventory inventory, Predicate<ItemStack> accepted) {
		List<Integer> slots = new ArrayList<>();
		for (int slot : dropzoneSlots) {
			ItemStack rawStack = inventory.getItem(slot);
			if (rawStack == null || rawStack.getType() == Material.AIR) continue;
			if (!accepted.test(rawStack)) continue;

			slots.add(slot);
		}
		return slots;
	}

	private List<ItemStack> collectOfferedItems(Inventory inventory, List<Integer> slots) {
		List<ItemStack> items = new ArrayList<>();
		for (int slot : slots) {
			ItemStack stack = inventory.getItem(slot);
			if (stack != null && stack.getType() != Material.AIR) items.add(stack.clone());
		}
		return items;
	}

	private void returnItemsToPlayer(Player viewer, BarterState state) {
		Inventory inv = state.inventory();
		if (inv == null) return;
		for (int slot : state.dropzoneSlots) {
			ItemStack stack = inv.getItem(slot);
			if (stack == null || stack.getType() == Material.AIR) continue;
			Map<Integer, ItemStack> leftover = viewer.getInventory().addItem(stack.clone());
			for (ItemStack drop : leftover.values()) {
				viewer.getWorld().dropItemNaturally(viewer.getLocation(), drop);
			}
			inv.setItem(slot, null);
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

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}

	private int tryPlaceInDropzone(BarterState state, Inventory inventory, ItemStack stack) {
		int remaining  = stack.getAmount();
		int maxPerSlot = stack.getMaxStackSize();

		for (int slot : state.dropzoneSlots) {
			if (remaining <= 0) break;
			ItemStack current = inventory.getItem(slot);
			if (current == null || current.getType() == Material.AIR) {
				ItemStack placed = stack.clone();
				int       amount = Math.min(remaining, maxPerSlot);
				placed.setAmount(amount);
				inventory.setItem(slot, placed);
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
			state.flow.rerender();
		});
	}

	static final class BarterState {
		final Player                       viewer;
		final TraderFlowSession            session;
		final int[]                        dropzoneSlots;
		final double                       barterMoodMultiplier;
		final BigDecimal                   askingValue;
		final MenuFlow<TraderFlowSession>  flow;

		BigDecimal   offeredValue = BigDecimal.ZERO;
		List<String> breakdown    = new ArrayList<>();

		BarterState(TraderFlowSession session, int[] dropzoneSlots, double barterMoodMultiplier,
		            BigDecimal askingValue, Player viewer, MenuFlow<TraderFlowSession> flow) {
			this.viewer               = viewer;
			this.session              = session;
			this.dropzoneSlots        = dropzoneSlots;
			this.barterMoodMultiplier = barterMoodMultiplier;
			this.askingValue          = askingValue;
			this.flow                 = flow;
		}

		/** The live top inventory for this session's menu, or {@code null} if the flow has no menu open right now
		 *  (defensive — every call site treats a null as "nothing to do", matching the old code's implicit
		 *  assumption that {@code state.handler.getInventory()} was always available). */
		Inventory inventory() {
			ChestMenu menu = flow.currentMenu();
			return menu != null ? menu.bukkitInventory() : null;
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
