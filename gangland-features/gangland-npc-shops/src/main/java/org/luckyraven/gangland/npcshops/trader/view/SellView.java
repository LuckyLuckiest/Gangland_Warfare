package org.luckyraven.gangland.npcshops.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.npcshops.events.trader.TraderSellRequestEvent;
import org.luckyraven.gangland.npcshops.listener.trader.TraderSellSessionListener;
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
 * <p>WS2 G4 re-point (§0d): see {@link BarterView}'s class doc — same {@link DropzoneSlotComponent}-based
 * item-return-contract shape, same {@link #onFlowEnd(Player)} cleanup hook wired once by {@link TraderFlow}.
 *
 * <p>The {@link SellState} is kept in a per-player {@link WeakHashMap} on this panel instance rather than on
 * {@link TraderFlowSession} — the state is large (item dropzone mirror, breakdown lines, mood multiplier) and only
 * meaningful while the viewer is actively in this panel. Entry populates the map in {@link #render}.
 *
 * <p>Bukkit click / drag events still flow through
 * {@link TraderSellSessionListener} which dispatches to {@link #handleClick}/{@link #handleDrag} — those look the
 * viewer up in the per-player state and update the offer display in-place (a full {@link MenuFlow#rerender()},
 * which is now safe against dropzone contents — see {@link DropzoneSlotComponent}).
 */
@RequiredArgsConstructor
public final class SellView implements Panel<TraderFlowSession>, BeanLifecycle {

	private static final int ROWS         = 6;
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
	public int rows(TraderFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(TraderFlowSession session) {
		return "&8Sell to&r &8&l[&b&l" + session.trait.displayName() + "&8&l]";
	}

	@Override
	public void render(MenuFlow<TraderFlowSession> flow, ChestMenuBuilder builder, TraderFlowSession session) {
		Player    viewer   = flow.viewer();
		SellState existing = active.get(viewer);
		SellState state;
		if (existing == null) {
			double mood = moodService.priceMultiplier(session.trader.getData().getId(), viewer.getUniqueId(),
			                                          session.trait.profile());
			// Sell prices are inverse: friendly mood (< 1x on buy) should pay more on sell, so invert for sell usage.
			double sellMood = 2.0 - mood;
			int[]  slots    = dropzoneSlots(settings.getSellMaxOfferSlots());
			state = new SellState(session, slots, sellMood, viewer, flow);
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

	// ── Listener bridges (invoked by TraderSellSessionListener) ──────────

	public ClickOutcome handleClick(Player viewer, Inventory inventory, Inventory clickedInventory, int slot,
	                                InventoryAction action, ItemStack currentItem) {
		SellState state = active.get(viewer);
		Inventory top   = state == null ? null : state.inventory();
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
		SellState state = active.get(viewer);
		Inventory top   = state == null ? null : state.inventory();
		if (state == null || top == null || top != inventory) return false;

		int topSize = top.getSize();
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
			returnItemsToPlayer(viewer, state);
			try {
				viewer.closeInventory();
			} catch (Exception ignored) { }
		}
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void renderChrome(ChestMenuBuilder builder, SellState state) {
		renderTrait(builder, state);
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

	private void renderTrait(ChestMenuBuilder builder, SellState state) {
		ItemBuilder trait = new ItemBuilder(material(XMaterial.DIAMOND, Material.DIAMOND));
		trait.setDisplayName("&d&lTrait: &d" + state.session.trait.displayName())
		     .setLore("&7This trader's valuation style.", " ", "&8Drop items on the left to get an offer.");
		builder.slot(SLOT_TRAIT, ItemComponent.of(trait));
	}

	private void renderMood(ChestMenuBuilder builder, SellState state) {
		double      mood = 2.0 - state.sellMoodMultiplier;
		ItemBuilder pane = new ItemBuilder(material(XMaterial.NETHER_STAR, Material.NETHER_STAR));
		pane.setDisplayName("&b&lMood: " + moodLabel(mood))
		    .setLore("&7Sell multiplier:",
		             "&e" + String.format("%.2fx", state.sellMoodMultiplier),
		             " ",
		             "&8Friendlier traders pay closer to base price.");
		builder.slot(SLOT_MOOD, ItemComponent.of(pane));
	}

	private String moodLabel(double multiplier) {
		if (multiplier <= 0.95) return "&aFriendly";
		return "&fNeutral";
	}

	private void renderOffer(ChestMenuBuilder builder, SellState state) {
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
		builder.slot(SLOT_OFFER, ItemComponent.of(offer));
	}

	private void renderBack(ChestMenuBuilder builder, SellState state) {
		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to menu")
		                                                  .setLore("&7Return your items and go back.");
		builder.slot(SLOT_BACK, ItemComponent.of(back).onAnyClick(ctx -> onBack(ctx.player(), state)));
	}

	private void renderClear(ChestMenuBuilder builder, SellState state) {
		ItemBuilder clear = new ItemBuilder(material(XMaterial.HOPPER, Material.HOPPER));
		clear.setDisplayName("&eClear offer").setLore("&7Return all offered items to your inventory.");
		builder.slot(SLOT_CLEAR, ItemComponent.of(clear).onAnyClick(ctx -> onClear(ctx.player(), state)));
	}

	private void renderConfirm(ChestMenuBuilder builder, SellState state) {
		boolean hasOffer = state.offeredTotal.signum() > 0;
		ItemStack icon = hasOffer
		                 ? material(XMaterial.LIME_WOOL, Material.GREEN_WOOL)
		                 : material(XMaterial.GRAY_WOOL, Material.GRAY_WOOL);
		ItemBuilder confirm = new ItemBuilder(icon);
		confirm.setDisplayName(hasOffer
		                       ? "&aCONFIRM — $" + NumberUtil.valueFormat(state.offeredTotal)
		                       : "&8Nothing to sell")
		       .setLore("&7Sell for &6$" + NumberUtil.valueFormat(state.offeredTotal) + "&7.");
		builder.slot(SLOT_CONFIRM, ItemComponent.of(confirm).onAnyClick(ctx -> onConfirm(ctx.player(), state)));
	}

	// ── Offer computation ─────────────────────────────────────────────────

	private void recomputeOffer(SellState state) {
		Inventory inv = state.inventory();
		if (inv == null) return;

		BigDecimal   total     = BigDecimal.ZERO;
		List<String> breakdown = new ArrayList<>();

		for (int slot : state.dropzoneSlots) {
			ItemStack rawStack = inv.getItem(slot);
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
		active.remove(viewer);
		state.flow.back();
		Bukkit.getScheduler().runTask(plugin, () -> SOUND_CANCEL.playSound(viewer));
	}

	private void onClear(Player viewer, SellState state) {
		returnItemsToPlayer(viewer, state);
		state.flow.rerender();
	}

	private void onConfirm(Player viewer, SellState state) {
		if (state.offeredTotal.signum() <= 0) return;

		Inventory inv = state.inventory();
		if (inv == null) return;

		// Walk the dropzone once, collecting only items the valuator accepted. No-offer items stay put so the return
		// pass hands them back instead of silently consuming them on confirm.
		List<ItemStack> soldItems = new ArrayList<>();
		List<Integer>   soldSlots = new ArrayList<>();
		for (int slot : state.dropzoneSlots) {
			ItemStack rawStack = inv.getItem(slot);
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

		for (int slot : soldSlots) inv.setItem(slot, null);
		returnItemsToPlayer(viewer, state);
		active.remove(viewer);
		state.flow.back();
		Bukkit.getScheduler().runTask(plugin, () -> SOUND_CONFIRM.playSound(viewer));
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private void returnItemsToPlayer(Player viewer, SellState state) {
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

	private int tryPlaceInDropzone(SellState state, Inventory inventory, ItemStack stack) {
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

	private void scheduleRecompute(Player viewer, SellState state) {
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (active.get(viewer) != state) return;
			state.flow.rerender();
		});
	}

	public static final class SellState {
		final Player                      viewer;
		final TraderFlowSession           session;
		final int[]                       dropzoneSlots;
		final double                      sellMoodMultiplier;
		final MenuFlow<TraderFlowSession> flow;

		@Getter
		BigDecimal baseOffer = BigDecimal.ZERO;
		BigDecimal   offeredTotal = BigDecimal.ZERO;
		List<String> breakdown    = new ArrayList<>();

		SellState(TraderFlowSession session, int[] dropzoneSlots, double sellMoodMultiplier, Player viewer,
		          MenuFlow<TraderFlowSession> flow) {
			this.viewer             = viewer;
			this.session            = session;
			this.dropzoneSlots      = dropzoneSlots;
			this.sellMoodMultiplier = sellMoodMultiplier;
			this.flow               = flow;
		}

		/** The live top inventory for this session's menu, or {@code null} if the flow has no menu open right now. */
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
