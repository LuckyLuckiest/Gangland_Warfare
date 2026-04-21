package me.luckyraven.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.luckyraven.copsncrooks.events.trader.TraderSellRequestEvent;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.autowire.bean.BeanLifecycle;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.valuation.ItemValuation;
import me.luckyraven.shop.valuation.SellValuator;
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

@RequiredArgsConstructor
public final class SellView implements BeanLifecycle {

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

	private static final SoundConfiguration SOUND_CONFIRM = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundConfiguration SOUND_CANCEL  = vanilla("ENTITY_VILLAGER_NO", 1.0f);

	private final JavaPlugin            plugin;
	private final MoodService           moodService;
	private final SellValuator          valuator;
	private final ItemRefresherRegistry refresherRegistry;
	private final TraderSettings        settings;
	private final ShopDisplayResolver   displayResolver;
	private final Map<Player, Session>  active = new WeakHashMap<>();
	@Setter
	private       ModeSelectView        modeSelectView;

	private static SoundConfiguration vanilla(String name, float pitch) {
		return new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, name, 0.6f, pitch);
	}

	private static boolean contains(int[] arr, int value) {
		for (int v : arr) {
			if (v == value) return true;
		}
		return false;
	}

	public void open(Player viewer, TraderNpc trader, ShopDefinition def, TraderTraitDefinition trait) {
		double mood = moodService.priceMultiplier(trader.getData().getId(), viewer.getUniqueId(), trait.profile());
		// Sell prices are inverse: mood multiplier is applied to base × ratio. A "friendly" trader (mood > 0, multiplier
		// < 1 on buy) should pay more on sell — so we invert for sell usage.
		double sellMood = 2.0 - mood;

		int[] slots = dropzoneSlots(settings.getSellMaxOfferSlots());
		InventoryHandler handler = new InventoryHandler(plugin, "&8Sell to&r &8&l[&b&l" + trait.displayName() + "&8&l]",
		                                                SIZE, viewer);

		Session session = new Session(viewer, trader, def, trait, handler, slots, sellMood);
		active.put(viewer, session);

		// Register dropzone slots with the inventory-api so InventoryClickHandler doesn't auto-cancel placement clicks.
		for (int slot : slots) {
			handler.setItem(slot, null, true);
		}

		renderChrome(session);

		handler.open(viewer);
	}

	public Session getSession(Player viewer) {
		return active.get(viewer);
	}

	// ── Rendering ────────────────────────────────────────────────────────

	@Override
	public void onShutdown() {
		// Plugin is stopping — return every in-flight sell offering to its owner and close the GUI so nothing is
		// consumed by an accidental confirm or a stale event that never fires.
		List<Player> viewers = new ArrayList<>(active.keySet());
		for (Player viewer : viewers) {
			Session session = active.remove(viewer);
			if (session == null) continue;
			session.committed          = true;          // keep the close listener from returning items again
			session.returnToModeSelect = false;         // don't try to reopen the mode-select on disable
			returnItemsToPlayer(viewer, session);
			try {
				viewer.closeInventory();
			} catch (Exception ignored) {
				// Server is tearing down; swallow any late close errors.
			}
		}
	}

	public void recomputeOffer(Session session) {
		BigDecimal   total     = BigDecimal.ZERO;
		List<String> breakdown = new ArrayList<>();

		for (int slot : session.dropzoneSlots) {
			ItemStack rawStack = session.handler.getInventory().getItem(slot);
			if (rawStack == null || rawStack.getType() == Material.AIR) {
				continue;
			}
			// Decorate so custom-item display names reach the valuator and the breakdown, but runtime state
			// (ammo, durability) stays as the player left it.
			ItemStack decorated = refresherRegistry.decorate(rawStack, session.viewer);
			ItemValuation valuation = valuator.value(session.definition, decorated,
			                                         session.trait.profile().sellPriceRatio(),
			                                         session.sellMoodMultiplier);
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

		session.baseOffer    = total;
		session.offeredTotal = total;
		session.breakdown    = breakdown;
	}

	/**
	 * Click handler. Returns {@code true} when the event should be cancelled. Handles both top-inventory dropzone
	 * clicks (allow, schedule recompute) and shift-click-from-bottom (collect into dropzone, cancel the vanilla move).
	 */
	public ClickOutcome handleClick(Player viewer, org.bukkit.inventory.Inventory inventory,
	                                org.bukkit.inventory.Inventory clickedInventory, int slot, InventoryAction action,
	                                ItemStack currentItem) {
		Session session = active.get(viewer);
		if (session == null || session.handler.getInventory() != inventory) {
			return ClickOutcome.PASS;
		}
		org.bukkit.inventory.Inventory top = session.handler.getInventory();

		if (clickedInventory == top) {
			boolean dropzone = contains(session.dropzoneSlots, slot);
			if (!dropzone) return ClickOutcome.PASS;
			scheduleRecompute(viewer, session);
			return ClickOutcome.ALLOW;
		}

		if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
			if (currentItem == null || currentItem.getType() == Material.AIR) {
				return ClickOutcome.PASS;
			}
			int placed = tryPlaceInDropzone(session, currentItem.clone());
			if (placed > 0) {
				ItemStack remaining = currentItem.clone();
				remaining.setAmount(currentItem.getAmount() - placed);
				ItemStack replacement = remaining.getAmount() > 0 ? remaining : null;
				scheduleRecompute(viewer, session);
				return new ClickOutcome(true, replacement);
			}
			return ClickOutcome.CANCEL_ONLY;
		}
		return ClickOutcome.PASS;
	}

	/**
	 * Drag handler. Returns {@code true} if the drag touched any non-dropzone top slot and must be cancelled.
	 */
	public boolean handleDrag(Player viewer, org.bukkit.inventory.Inventory inventory,
	                          java.util.Collection<Integer> rawSlots) {
		Session session = active.get(viewer);
		if (session == null || session.handler.getInventory() != inventory) return false;

		int topSize = session.handler.getInventory().getSize();
		for (int raw : rawSlots) {
			if (raw < topSize && !contains(session.dropzoneSlots, raw)) {
				return true;
			}
		}
		scheduleRecompute(viewer, session);
		return false;
	}

	/**
	 * Close handler — returns items (if not committed) and optionally reopens the mode-select view.
	 */
	public void handleClose(Player viewer, org.bukkit.inventory.Inventory inventory) {
		Session session = active.get(viewer);
		if (session == null || session.handler.getInventory() != inventory) return;
		if (session.pendingSubview) return;

		if (!session.committed) {
			returnItemsToPlayer(viewer, session);
		}
		active.remove(viewer);

		if (session.returnToModeSelect && modeSelectView != null && session.definition != null) {
			Bukkit.getScheduler()
			      .runTask(plugin,
			               () -> modeSelectView.open(viewer, session.trader, session.definition, session.trait));
		}
	}

	private void renderChrome(Session session) {
		session.handler.getInventory().setItem(SLOT_TRAIT, null);
		session.handler.getInventory().setItem(SLOT_OFFER, null);
		session.handler.getInventory().setItem(SLOT_MOOD, null);
		session.handler.getInventory().setItem(SLOT_BACK, null);
		session.handler.getInventory().setItem(SLOT_CLEAR, null);
		session.handler.getInventory().setItem(SLOT_CONFIRM, null);

		renderTrait(session);
		renderOffer(session);
		renderMood(session);
		renderBack(session);
		renderClear(session);
		renderConfirm(session);

		// Snapshot dropzone contents, fill decorative empty slots, then restore dropzones so the filler doesn't claim
		// them (they must remain truly empty so players can drop items freely and closing the view doesn't return
		// filler glass back into their inventory).
		ItemStack[] preservedDropzone = new ItemStack[session.dropzoneSlots.length];
		for (int i = 0; i < session.dropzoneSlots.length; i++) {
			preservedDropzone[i] = session.handler.getInventory().getItem(session.dropzoneSlots[i]);
			session.handler.getInventory().setItem(session.dropzoneSlots[i], new ItemStack(Material.BARRIER));
		}

		InventoryUtil.fillInventory(session.handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		for (int i = 0; i < session.dropzoneSlots.length; i++) {
			session.handler.getInventory().setItem(session.dropzoneSlots[i], preservedDropzone[i]);
		}
	}

	private void renderTrait(Session session) {
		ItemBuilder trait = new ItemBuilder(material(XMaterial.DIAMOND, Material.DIAMOND));
		trait.setDisplayName("&d&lTrait: &d" + session.trait.displayName())
		     .setLore("&7This trader's valuation style.",
		              " ",
		              "&8Drop items on the left to get an offer.");
		session.handler.setItem(SLOT_TRAIT, trait, false, (p, inv, b) -> { });
	}

	private void renderMood(Session session) {
		double      mood = 2.0 - session.sellMoodMultiplier;
		ItemBuilder pane = new ItemBuilder(material(XMaterial.NETHER_STAR, Material.NETHER_STAR));
		pane.setDisplayName("&b&lMood: " + moodLabel(mood))
		    .setLore("&7Sell multiplier:",
		             "&e" + String.format("%.2fx", session.sellMoodMultiplier),
		             " ",
		             "&8Friendlier traders pay closer to base price.");
		session.handler.setItem(SLOT_MOOD, pane, false, (p, inv, b) -> { });
	}

	private String moodLabel(double multiplier) {
		if (multiplier <= 0.95) return "&aFriendly";
		return "&fNeutral";
	}

	private void renderOffer(Session session) {
		recomputeOffer(session);

		ItemBuilder offer = new ItemBuilder(material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT)).setDisplayName(
				"&6Offer: &e$" + NumberUtil.valueFormat(session.offeredTotal));
		List<String> lore = new ArrayList<>();
		if (session.breakdown.isEmpty()) {
			lore.add("&8Drop items to the left to get an offer.");
		} else {
			int shown = Math.min(session.breakdown.size(), 5);
			for (int i = 0; i < shown; i++) {
				lore.add(session.breakdown.get(i));
			}
			if (session.breakdown.size() > shown) {
				lore.add("&8…and " + (session.breakdown.size() - shown) + " more");
			}
		}
		offer.setLore(lore);
		session.handler.setItem(SLOT_OFFER, offer, false, (p, inv, b) -> { });
	}

	private void renderBack(Session session) {
		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to menu")
		                                                  .setLore("&7Return your items and go back.");
		session.handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> onBack(p, session));
	}

	// ── Actions ──────────────────────────────────────────────────────────

	private void renderClear(Session session) {
		ItemBuilder clear = new ItemBuilder(material(XMaterial.HOPPER, Material.HOPPER));
		clear.setDisplayName("&eClear offer").setLore("&7Return all offered items to your inventory.");
		session.handler.setItem(SLOT_CLEAR, clear, false, (p, inv, b) -> onClear(p, session));
	}

	private void renderConfirm(Session session) {
		boolean hasOffer = session.offeredTotal.signum() > 0;
		ItemStack icon = hasOffer ?
		                 material(XMaterial.LIME_WOOL, Material.GREEN_WOOL) :
		                 material(XMaterial.GRAY_WOOL, Material.GRAY_WOOL);
		ItemBuilder confirm = new ItemBuilder(icon);
		confirm.setDisplayName(hasOffer ?
		                       "&aCONFIRM — $" + NumberUtil.valueFormat(session.offeredTotal) :
		                       "&8Nothing to sell")
		       .setLore("&7Sell for &6$" + NumberUtil.valueFormat(session.offeredTotal) + "&7.");
		session.handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> onConfirm(p, session));
	}

	private void onBack(Player viewer, Session session) {
		SOUND_CANCEL.playSound(viewer);
		session.returnToModeSelect = true;
		viewer.closeInventory();
	}

	private void onClear(Player viewer, Session session) {
		returnItemsToPlayer(viewer, session);
		recomputeOffer(session);
		renderChrome(session);
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private void onConfirm(Player viewer, Session session) {
		if (session.offeredTotal.signum() <= 0) {
			return;
		}

		// Walk the dropzone once, collecting only items the valuator accepted. No-offer items stay in their slots so the
		// return pass below hands them back to the player instead of silently consuming them on confirm.
		List<ItemStack> soldItems = new ArrayList<>();
		List<Integer>   soldSlots = new ArrayList<>();
		for (int slot : session.dropzoneSlots) {
			ItemStack rawStack = session.handler.getInventory().getItem(slot);
			if (rawStack == null || rawStack.getType() == Material.AIR) {
				continue;
			}
			ItemStack decorated = refresherRegistry.decorate(rawStack, session.viewer);
			ItemValuation valuation = valuator.value(session.definition, decorated,
			                                         session.trait.profile().sellPriceRatio(),
			                                         session.sellMoodMultiplier);
			if (!valuation.hasValue()) {
				continue;
			}
			soldItems.add(rawStack.clone());
			soldSlots.add(slot);
		}

		TraderSellRequestEvent event = new TraderSellRequestEvent(viewer, session.trader, soldItems,
		                                                          session.offeredTotal);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			return;
		}

		SOUND_CONFIRM.playSound(viewer);
		session.committed = true;
		for (int slot : soldSlots) {
			session.handler.getInventory().setItem(slot, null);
		}
		// committed=true skips the handleClose return pass, so hand back the no-offer leftovers explicitly.
		returnItemsToPlayer(viewer, session);
		viewer.closeInventory();
	}

	// ── Session ──────────────────────────────────────────────────────────

	// ── Event bridges (invoked by the singleton TraderSellSessionListener) ──

	private void returnItemsToPlayer(Player viewer, Session session) {
		for (int slot : session.dropzoneSlots) {
			ItemStack stack = session.handler.getInventory().getItem(slot);
			if (stack == null || stack.getType() == Material.AIR) {
				continue;
			}
			Map<Integer, ItemStack> leftover = viewer.getInventory().addItem(stack.clone());
			for (ItemStack drop : leftover.values()) {
				viewer.getWorld().dropItemNaturally(viewer.getLocation(), drop);
			}
			session.handler.getInventory().setItem(slot, null);
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

	private int tryPlaceInDropzone(Session session, ItemStack stack) {
		int remaining  = stack.getAmount();
		int maxPerSlot = stack.getMaxStackSize();

		for (int slot : session.dropzoneSlots) {
			if (remaining <= 0) break;
			ItemStack current = session.handler.getInventory().getItem(slot);
			if (current == null || current.getType() == Material.AIR) {
				ItemStack placed = stack.clone();
				int       amount = Math.min(remaining, maxPerSlot);
				placed.setAmount(amount);
				session.handler.getInventory().setItem(slot, placed);
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

	private void scheduleRecompute(Player viewer, Session session) {
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (active.get(viewer) != session) return;
			recomputeOffer(session);
			renderOffer(session);
			renderConfirm(session);
		});
	}

	public static final class Session {
		final Player                viewer;
		@Getter
		final TraderNpc             trader;
		final ShopDefinition        definition;
		@Getter
		final TraderTraitDefinition trait;
		final InventoryHandler      handler;
		final int[]                 dropzoneSlots;
		final double                sellMoodMultiplier;

		@Getter
		BigDecimal baseOffer = BigDecimal.ZERO;
		BigDecimal   offeredTotal = BigDecimal.ZERO;
		List<String> breakdown    = new ArrayList<>();

		boolean pendingSubview     = false;
		boolean committed          = false;
		boolean returnToModeSelect = true;

		Session(Player viewer, TraderNpc trader, ShopDefinition definition, TraderTraitDefinition trait,
		        InventoryHandler handler, int[] dropzoneSlots, double sellMoodMultiplier) {
			this.viewer             = viewer;
			this.trader             = trader;
			this.definition         = definition;
			this.trait              = trait;
			this.handler            = handler;
			this.dropzoneSlots      = dropzoneSlots;
			this.sellMoodMultiplier = sellMoodMultiplier;
		}
	}

	/**
	 * Result of {@link #handleClick}. {@link #cancel} says whether Bukkit should cancel the event; when the click moved
	 * an item into the dropzone via shift-click, {@link #replacementCurrent} is the residual stack to leave in the
	 * bottom inventory slot.
	 */
	public record ClickOutcome(boolean cancel, ItemStack replacementCurrent, boolean replace) {
		public static final ClickOutcome PASS        = new ClickOutcome(false, null, false);
		public static final ClickOutcome ALLOW       = new ClickOutcome(false, null, false);
		public static final ClickOutcome CANCEL_ONLY = new ClickOutcome(true, null, false);

		public ClickOutcome(boolean cancel, ItemStack replacementCurrent) {
			this(cancel, replacementCurrent, true);
		}
	}

}
