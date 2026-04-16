package me.luckyraven.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.luckyraven.copsncrooks.events.trader.TraderSellRequestEvent;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.message.TraderMessageContract;
import me.luckyraven.copsncrooks.npc.trader.mood.BargainResult;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.valuation.ItemValuation;
import me.luckyraven.shop.valuation.SellValuator;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.util.utilities.NumberUtil;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

@RequiredArgsConstructor
public final class SellView implements BeanLifecycle {

	private static final int SIZE         = 54;
	private static final int SLOT_OFFER   = 26;
	private static final int SLOT_BARGAIN = 35;
	private static final int SLOT_BACK    = 45;
	private static final int SLOT_PROPOSE = 46;
	private static final int SLOT_CLEAR   = 48;
	private static final int SLOT_CONFIRM = 50;
	private static final int SLOT_CANCEL  = 53;

	private static final int[] ALL_DROPZONE_SLOTS = {18, 19, 20, 21, 22, 23, 24, 27, 28, 29, 30, 31, 32, 33};

	// Defence in depth: even if some future code path seeds session.playerProposedPrice directly, recomputeOffer
	// will never let offeredTotal exceed baseOffer × this cap. MoodService.evaluateProposedSellPrice already
	// enforces the trader's profit margin — this cap guards against anyone bypassing the evaluator.
	private static final double PROPOSE_HARD_CAP = 1.0D;

	private static final SoundConfiguration SOUND_CONFIRM = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundConfiguration SOUND_CANCEL  = vanilla("ENTITY_VILLAGER_NO", 1.0f);
	private static final SoundConfiguration SOUND_CLICK   = vanilla("UI_BUTTON_CLICK", 1.0f);

	private final JavaPlugin            plugin;
	private final MoodService           moodService;
	private final SellValuator          valuator;
	private final ItemRefresherRegistry refresherRegistry;
	private final TraderSettings        settings;
	private final TraderMessageContract messages;
	private final ShopDisplayResolver   displayResolver;
	private final Map<Player, Session>  active = new WeakHashMap<>();
	@Setter
	private       ModeSelectView        modeSelectView;
	@Setter
	private       SellBargainView       bargainView;

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

	public void reopenAfterSubview(Player viewer) {
		Session session = active.get(viewer);
		if (session == null) {
			return;
		}
		session.pendingSubview = false;
		if (session.traderAngry) {
			// Trader hard-rejected the propose/bargain — return items, drop the session, don't reopen.
			session.returnToModeSelect = false;
			returnItemsToPlayer(viewer, session);
			active.remove(viewer);
			return;
		}
		renderChrome(session);
		session.handler.open(viewer);
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
		double       total     = 0.0;
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
				double lineTotal = valuation.unitPrice() * decorated.getAmount();
				total += lineTotal;
				breakdown.add(
						"&7" + label + " x" + decorated.getAmount() + " &8→ &6$" + NumberUtil.valueFormat(lineTotal));
			} else {
				breakdown.add("&8" + label + " x" + decorated.getAmount() + " &8→ &cno offer");
			}
		}

		session.baseOffer = total;
		// Clamp the player-proposed price to a hard ceiling so no code path can push offeredTotal above fair value.
		double fairCeiling    = total * PROPOSE_HARD_CAP;
		double cappedProposed = Math.min(session.playerProposedPrice, fairCeiling);
		double floor          = Math.max(total, session.bargainOverride);
		session.offeredTotal = Math.max(floor, cappedProposed);
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
		session.handler.getInventory().setItem(SLOT_OFFER, null);
		session.handler.getInventory().setItem(SLOT_BARGAIN, null);
		session.handler.getInventory().setItem(SLOT_BACK, null);
		session.handler.getInventory().setItem(SLOT_CANCEL, null);
		session.handler.getInventory().setItem(SLOT_PROPOSE, null);
		session.handler.getInventory().setItem(SLOT_CLEAR, null);
		session.handler.getInventory().setItem(SLOT_CONFIRM, null);

		renderOffer(session);
		renderBargain(session);
		renderBack(session);
		renderCancel(session);
		renderPropose(session);
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
		lore.add(" ");
		lore.add("&7Bargain rounds: &f" + session.bargainRoundsUsed + "&7/&f" +
		         session.trait.profile().bargainMaxRounds());
		offer.setLore(lore);
		session.handler.setItem(SLOT_OFFER, offer, false, (p, inv, b) -> { });
	}

	private void renderBargain(Session session) {
		if (!session.trait.profile().allowsBargaining()) {
			return;
		}
		ItemBuilder bargain = new ItemBuilder(material(XMaterial.BOOK, Material.BOOK));
		bargain.setDisplayName("&eBARGAIN")
		       .setLore("&7Ask for a higher offer.",
		                "&8Rounds: " + session.bargainRoundsUsed + "/" + session.trait.profile().bargainMaxRounds());
		session.handler.setItem(SLOT_BARGAIN, bargain, false, (p, inv, b) -> onBargain(p, session));
	}

	private void renderBack(Session session) {
		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to menu");
		session.handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> onBack(p, session));
	}

	// ── Actions ──────────────────────────────────────────────────────────

	private void renderCancel(Session session) {
		ItemBuilder cancel = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER));
		cancel.setDisplayName("&cCancel").setLore("&7Return items and leave.");
		session.handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> onCancel(p, session));
	}

	private void renderClear(Session session) {
		ItemBuilder clear = new ItemBuilder(material(XMaterial.HOPPER, Material.HOPPER));
		clear.setDisplayName("&eClear offer").setLore("&7Return all offered items to your inventory.");
		session.handler.setItem(SLOT_CLEAR, clear, false, (p, inv, b) -> onClear(p, session));
	}

	private void renderPropose(Session session) {
		ItemBuilder propose = new ItemBuilder(material(XMaterial.NAME_TAG, Material.NAME_TAG));
		propose.setDisplayName("&ePropose price")
		       .setLore("&7Type your own asking price.", session.playerProposedPrice > 0 ?
		                                                 "&7Current proposal: &6$" +
		                                                 NumberUtil.valueFormat(session.playerProposedPrice) :
		                                                 "&8No proposal set.");
		session.handler.setItem(SLOT_PROPOSE, propose, false, (p, inv, b) -> onPropose(p, session));
	}

	private void renderConfirm(Session session) {
		ItemStack icon = session.offeredTotal > 0 ?
		                 material(XMaterial.LIME_WOOL, Material.GREEN_WOOL) :
		                 material(XMaterial.GRAY_WOOL, Material.GRAY_WOOL);
		ItemBuilder confirm = new ItemBuilder(icon);
		confirm.setDisplayName(session.offeredTotal > 0 ?
		                       "&aCONFIRM — $" + NumberUtil.valueFormat(session.offeredTotal) :
		                       "&8Nothing to sell")
		       .setLore("&7Sell for &6$" + NumberUtil.valueFormat(session.offeredTotal) + "&7.");
		session.handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> onConfirm(p, session));
	}

	private void onBargain(Player viewer, Session session) {
		if (bargainView == null) {
			return;
		}
		if (session.baseOffer <= 0) {
			viewer.sendMessage(ChatUtil.color("&cNothing to bargain."));
			return;
		}
		if (session.bargainRoundsUsed >= session.trait.profile().bargainMaxRounds()) {
			return;
		}
		SOUND_CLICK.playSound(viewer);
		session.pendingSubview = true;
		bargainView.open(viewer, session, this::reopenAfterSubview);
	}

	private void onBack(Player viewer, Session session) {
		SOUND_CLICK.playSound(viewer);
		session.returnToModeSelect = true;
		viewer.closeInventory();
	}

	private void onCancel(Player viewer, Session session) {
		SOUND_CANCEL.playSound(viewer);
		session.returnToModeSelect = false;
		viewer.closeInventory();
	}

	private void onClear(Player viewer, Session session) {
		returnItemsToPlayer(viewer, session);
		session.playerProposedPrice = 0.0;
		session.bargainOverride     = 0.0;
		recomputeOffer(session);
		renderChrome(session);
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private void onPropose(Player viewer, Session session) {
		SOUND_CLICK.playSound(viewer);
		session.pendingSubview = true;

		double initial     = session.playerProposedPrice > 0 ? session.playerProposedPrice : session.baseOffer;
		String initialText = initial > 0 ? String.valueOf((long) initial) : "";

		AnvilGUI.Builder builder = new AnvilGUI.Builder();
		builder.plugin(plugin)
		       .title("Propose your price")
		       .itemLeft(material(XMaterial.NAME_TAG, Material.NAME_TAG))
		       .text(initialText)
		       .onClick((slot, state) -> {
				   if (slot != AnvilGUI.Slot.OUTPUT) {
					   return Collections.emptyList();
				   }
				   String raw = state.getText() == null ? "" : state.getText().trim();
				   double value;
				   try {
					   value = Double.parseDouble(raw);
				   } catch (NumberFormatException e) {
					   viewer.sendMessage(messages.sellProposePriceInvalid());
					   return Collections.emptyList();
				   }
				   if (value <= 0 || Double.isNaN(value) || Double.isInfinite(value)) {
					   viewer.sendMessage(messages.sellProposePriceInvalid());
					   return Collections.emptyList();
				   }

				   // Evaluate against the trader's fair-value estimate (today = local valuator; future = market
				   // service). No bargain round is consumed. If nothing is in the drop-zone yet the evaluator
				   // returns REJECTED, which falls through to the normal reject branch — no "invalid number" yell.
				   double fairValue = session.baseOffer;
				   BargainResult result = moodService.evaluateProposedSellPrice(session.trader.getData().getId(),
			                                                                    viewer.getUniqueId(),
			                                                                    session.trait.profile(), value,
			                                                                    fairValue);

				   switch (result.outcome()) {
					   case ACCEPTED -> {
						   session.playerProposedPrice = result.resolvedPrice();
						   viewer.sendMessage(messages.sellProposePriceAccepted(result.resolvedPrice()));
					   }
					   case COUNTER_OFFER -> {
						   session.playerProposedPrice = result.resolvedPrice();
						   viewer.sendMessage(messages.bargainCounterOffer(result.resolvedPrice()));
					   }
					   case REJECTED -> {
						   session.playerProposedPrice = 0.0;
						   viewer.sendMessage(messages.bargainRejected());
					   }
					   case HARD_REJECTED -> {
						   session.playerProposedPrice = 0.0;
					       session.traderAngry         = true;
						   viewer.sendMessage(messages.sellProposePriceAngered());
					   }
				   }
				   return List.of(AnvilGUI.ResponseAction.close());
			   })
		       .onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> {
				   session.pendingSubview = false;
				   if (session.traderAngry) {
					   // Trader hard-rejected — return items, don't reopen the sell view.
					   session.returnToModeSelect = false;
					   returnItemsToPlayer(viewer, session);
					   active.remove(viewer);
					   return;
				   }
				   recomputeOffer(session);
				   renderChrome(session);
				   session.handler.open(viewer);
			   }))
		       .open(viewer);
	}

	private void onConfirm(Player viewer, Session session) {
		if (session.offeredTotal <= 0) {
			return;
		}

		List<ItemStack> offered = collectOfferedItems(session);

		TraderSellRequestEvent event = new TraderSellRequestEvent(viewer, session.trader, offered,
		                                                          session.offeredTotal);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			return;
		}

		SOUND_CONFIRM.playSound(viewer);
		session.committed = true;
		// Clear dropzone slots without returning items — they're consumed by the sale.
		for (int slot : session.dropzoneSlots) {
			session.handler.getInventory().setItem(slot, null);
		}
		viewer.closeInventory();
	}

	// ── Session ──────────────────────────────────────────────────────────

	private List<ItemStack> collectOfferedItems(Session session) {
		List<ItemStack> items = new ArrayList<>();
		for (int slot : session.dropzoneSlots) {
			ItemStack stack = session.handler.getInventory().getItem(slot);
			if (stack != null && stack.getType() != Material.AIR) {
				items.add(stack.clone());
			}
		}
		return items;
	}

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
			session.bargainOverride = 0.0;
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
		double baseOffer           = 0.0;
		@Setter
		double bargainOverride     = 0.0;
		@Setter
		@Getter
		double playerProposedPrice = 0.0;
		double offeredTotal = 0.0;
		@Getter
		int bargainRoundsUsed = 0;
		List<String> breakdown = new ArrayList<>();

		boolean pendingSubview     = false;
		boolean committed          = false;
		boolean returnToModeSelect = true;
		// Set by a HARD_REJECTED bargain/propose — makes the next close skip the mode-select re-open and return items.
		boolean traderAngry        = false;

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

		public void incrementBargainRound() {
			bargainRoundsUsed++;
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
