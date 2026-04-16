package me.luckyraven.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.events.trader.TraderTradeInRequestEvent;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.ShopItemEntry;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.valuation.ItemValuation;
import me.luckyraven.shop.valuation.SellValuator;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class TradeInView implements BeanLifecycle {

	private static final int SIZE         = 54;
	private static final int SLOT_OFFER   = 26;
	private static final int SLOT_BACK    = 45;
	private static final int SLOT_CLEAR   = 48;
	private static final int SLOT_CONFIRM = 50;
	private static final int SLOT_CANCEL  = 53;

	private static final int[] ALL_DROPZONE_SLOTS = {18, 19, 20, 21, 22, 23, 24, 27, 28, 29, 30, 31, 32, 33};

	private static final SoundConfiguration SOUND_CONFIRM = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundConfiguration SOUND_CANCEL  = vanilla("ENTITY_VILLAGER_NO", 1.0f);
	private static final SoundConfiguration SOUND_CLICK   = vanilla("UI_BUTTON_CLICK", 1.0f);

	private final JavaPlugin            plugin;
	private final MoodService           moodService;
	private final SellValuator          valuator;
	private final ItemRefresherRegistry refresherRegistry;
	private final ShopDisplayResolver   displayResolver;
	private final TraderSettings        settings;

	private final Map<Player, Session> active = new WeakHashMap<>();

	private static SoundConfiguration vanilla(String name, float pitch) {
		return new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, name, 0.6f, pitch);
	}

	private static boolean contains(int[] arr, int value) {
		for (int v : arr) {
			if (v == value) return true;
		}
		return false;
	}

	@Override
	public void onShutdown() {
		// Plugin is stopping — return every in-flight trade-in drop-zone stack to its owner and close the GUI so
		// nothing is consumed by an accidental confirm or a stale event that never fires.
		List<Player> viewers = new ArrayList<>(active.keySet());
		for (Player viewer : viewers) {
			Session session = active.remove(viewer);
			if (session == null) continue;
			session.committed           = true;  // keep the close listener from double-returning items
			session.returnToNegotiation = false; // don't try to reopen the negotiation view on disable
			returnItemsToPlayer(viewer, session);
			try {
				viewer.closeInventory();
			} catch (Exception ignored) {
				// Server is tearing down; swallow any late close errors.
			}
		}
	}

	public void open(Player viewer, NegotiationView.NegotiationSession parent, ShopDefinition def, double buyPrice,
	                 Consumer<Player> onClose) {
		double mood = moodService.priceMultiplier(parent.getTrader().getData().getId(), viewer.getUniqueId(),
		                                          parent.getTrait().profile());
		double sellMood = 2.0 - mood;

		int[] slots = dropzoneSlots(settings.getSellMaxOfferSlots());

		ItemStack        decorated = refresherRegistry.decorate(parent.getEntry().getItem(), viewer);
		String           label     = displayResolver.cleanDisplayName(decorated);
		InventoryHandler handler   = new InventoryHandler(plugin, "&8Trade-in for " + label, SIZE, viewer);

		Session session = new Session(viewer, parent, def, parent.getEntry(), buyPrice, handler, slots, sellMood,
		                              onClose);
		active.put(viewer, session);

		// Register dropzone slots with the inventory-api so InventoryClickHandler doesn't auto-cancel placement clicks.
		for (int slot : slots) {
			handler.setItem(slot, null, true);
		}

		renderChrome(session);

		handler.open(viewer);
	}

	/**
	 * Click handler. Returns a {@link ClickOutcome} describing whether the event should be cancelled and whether
	 * {@code currentItem} should be replaced (after a successful shift-move into the drop-zone).
	 */
	public ClickOutcome handleClick(Player viewer, org.bukkit.inventory.Inventory inventory,
	                                org.bukkit.inventory.Inventory clickedInventory, int slot,
	                                InventoryAction action, ItemStack currentItem) {
		Session session = active.get(viewer);
		if (session == null || session.handler.getInventory() != inventory) {
			return ClickOutcome.PASS;
		}

		if (clickedInventory == session.handler.getInventory()) {
			if (!contains(session.dropzoneSlots, slot)) return ClickOutcome.PASS;
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

	public boolean handleDrag(Player viewer, org.bukkit.inventory.Inventory inventory,
	                          java.util.Collection<Integer> rawSlots) {
		Session session = active.get(viewer);
		if (session == null || session.handler.getInventory() != inventory) return false;

		int topSize = session.handler.getInventory().getSize();
		for (int raw : rawSlots) {
			if (raw < topSize && !contains(session.dropzoneSlots, raw)) return true;
		}
		scheduleRecompute(viewer, session);
		return false;
	}

	public void handleClose(Player viewer, org.bukkit.inventory.Inventory inventory) {
		Session session = active.get(viewer);
		if (session == null || session.handler.getInventory() != inventory) return;

		if (!session.committed) {
			returnItemsToPlayer(viewer, session);
		}
		active.remove(viewer);

		if (session.returnToNegotiation && session.onClose != null) {
			Bukkit.getScheduler().runTask(plugin, () -> session.onClose.accept(viewer));
		}
	}

	private void renderChrome(Session session) {
		session.handler.getInventory().setItem(SLOT_OFFER, null);
		session.handler.getInventory().setItem(SLOT_BACK, null);
		session.handler.getInventory().setItem(SLOT_CANCEL, null);
		session.handler.getInventory().setItem(SLOT_CLEAR, null);
		session.handler.getInventory().setItem(SLOT_CONFIRM, null);

		renderOffer(session);
		renderBack(session);
		renderCancel(session);
		renderClear(session);
		renderConfirm(session);

		// Dropzones must stay empty — see TraderSellView.renderChrome for the same guard.
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
		recomputeCredit(session);

		double owed   = Math.max(0.0, session.buyPrice - session.tradeInCredit);
		double refund = refundAmount(session);
		double excess = Math.max(0.0, session.rawCredit - session.buyPrice);

		ItemBuilder offer = new ItemBuilder(material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT)).setDisplayName(
				"&6Trade-in status");
		List<String> lore = new ArrayList<>();
		lore.add("&7Price: &f$" + NumberUtil.valueFormat(session.buyPrice));
		lore.add("&7Credit: &a$" + NumberUtil.valueFormat(session.tradeInCredit));
		lore.add("&7Owed: &c$" + NumberUtil.valueFormat(owed));
		if (refund > 0.0) {
			lore.add("&7Refund: &a+$" + NumberUtil.valueFormat(refund));
		} else if (excess > 0.0) {
			lore.add("&7Excess: &8$" + NumberUtil.valueFormat(excess) + " &8(trader keeps it)");
		}
		lore.add(" ");
		if (session.breakdown.isEmpty()) {
			lore.add("&8Drop items to the left for credit.");
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
		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to negotiation");
		session.handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> onBack(p, session));
	}

	private void renderCancel(Session session) {
		ItemBuilder cancel = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER));
		cancel.setDisplayName("&cCancel").setLore("&7Return items and leave.");
		session.handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> onCancel(p, session));
	}

	private void renderClear(Session session) {
		ItemBuilder clear = new ItemBuilder(material(XMaterial.HOPPER, Material.HOPPER)).setDisplayName(
				"&eClear offer");
		session.handler.setItem(SLOT_CLEAR, clear, false, (p, inv, b) -> onClear(p, session));
	}

	private void renderConfirm(Session session) {
		double  owed       = Math.max(0.0, session.buyPrice - session.tradeInCredit);
		boolean canConfirm = session.tradeInCredit > 0;
		boolean fullyPaid  = canConfirm && owed <= 0.0;

		// Green when credit covers the whole price; orange while some amount is still owed; gray when no credit yet.
		ItemStack icon;
		if (!canConfirm) {
			icon = material(XMaterial.GRAY_WOOL, Material.GRAY_WOOL);
		} else if (fullyPaid) {
			icon = material(XMaterial.LIME_WOOL, Material.GREEN_WOOL);
		} else {
			icon = material(XMaterial.ORANGE_WOOL, Material.ORANGE_WOOL);
		}

		String displayName;
		if (!canConfirm) {
			displayName = "&8No credit yet";
		} else if (fullyPaid) {
			displayName = "&aCONFIRM &7(fully covered)";
		} else {
			displayName = "&6CONFIRM &7(&c$" + NumberUtil.valueFormat(owed) + " &7owed)";
		}

		ItemBuilder confirm = new ItemBuilder(icon);
		confirm.setDisplayName(displayName)
		       .setLore("&7Pay &6$" + NumberUtil.valueFormat(owed) + " &7+ items to buy.");
		session.handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> onConfirm(p, session));
	}

	private void recomputeCredit(Session session) {
		double       credit    = 0.0;
		List<String> breakdown = new ArrayList<>();

		for (int slot : session.dropzoneSlots) {
			ItemStack rawStack = session.handler.getInventory().getItem(slot);
			if (rawStack == null || rawStack.getType() == Material.AIR) {
				continue;
			}
			// Decorate so custom display names reach the valuator/breakdown without resetting runtime state.
			ItemStack decorated = refresherRegistry.decorate(rawStack, session.viewer);
			ItemValuation valuation = valuator.value(session.definition, decorated,
			                                         session.parent.getTrait().profile().sellPriceRatio(),
			                                         session.sellMoodMultiplier);
			String label = displayResolver.cleanDisplayName(decorated);
			if (valuation.hasValue()) {
				double lineTotal = valuation.unitPrice() * decorated.getAmount();
				credit += lineTotal;
				breakdown.add(
						"&7" + label + " x" + decorated.getAmount() + " &8→ &a$" + NumberUtil.valueFormat(lineTotal));
			} else {
				breakdown.add("&8" + label + " x" + decorated.getAmount() + " &8→ &cno credit");
			}
		}

		session.rawCredit     = credit;
		session.tradeInCredit = Math.min(credit, session.buyPrice);
		session.breakdown     = breakdown;
	}

	/**
	 * Overpay refund the player receives on confirm — zero if the trader's trait doesn't refund overpay.
	 */
	private double refundAmount(Session session) {
		if (!session.parent.getTrait().profile().refundsTradeInOverpay()) return 0.0;
		return Math.max(0.0, session.rawCredit - session.buyPrice);
	}

	private void onBack(Player viewer, Session session) {
		SOUND_CLICK.playSound(viewer);
		session.returnToNegotiation = true;
		viewer.closeInventory();
	}

	private void onCancel(Player viewer, Session session) {
		SOUND_CANCEL.playSound(viewer);
		session.returnToNegotiation = true;
		viewer.closeInventory();
	}

	private void onClear(Player viewer, Session session) {
		returnItemsToPlayer(viewer, session);
		recomputeCredit(session);
		renderChrome(session);
	}

	private void onConfirm(Player viewer, Session session) {
		if (session.tradeInCredit <= 0) {
			return;
		}

		List<ItemStack> offered = collectOfferedItems(session);

		TraderTradeInRequestEvent event = new TraderTradeInRequestEvent(viewer, session.parent.getTrader(),
		                                                                session.entry, session.buyPrice,
		                                                                session.tradeInCredit, refundAmount(session),
		                                                                offered);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			return;
		}

		SOUND_CONFIRM.playSound(viewer);
		session.committed = true;
		for (int slot : session.dropzoneSlots) {
			session.handler.getInventory().setItem(slot, null);
		}
		session.returnToNegotiation = false;
		viewer.closeInventory();
	}

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

	// ── Event bridges (invoked by the singleton TradeInSessionListener) ──

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
			recomputeCredit(session);
			renderOffer(session);
			renderConfirm(session);
		});
	}

	static final class Session {
		final Player                             viewer;
		final NegotiationView.NegotiationSession parent;
		final ShopDefinition                     definition;
		final ShopItemEntry                      entry;
		final double                             buyPrice;
		final InventoryHandler                   handler;
		final int[]                              dropzoneSlots;
		final double                             sellMoodMultiplier;
		final Consumer<Player>                   onClose;

		double       rawCredit     = 0.0;  // unclamped total valuation of dropped items
		double       tradeInCredit = 0.0;  // clamped to buyPrice; excess goes to refund if the trait allows
		List<String> breakdown     = new ArrayList<>();

		boolean committed           = false;
		boolean returnToNegotiation = true;

		Session(Player viewer, NegotiationView.NegotiationSession parent, ShopDefinition definition,
		        ShopItemEntry entry, double buyPrice, InventoryHandler handler, int[] dropzoneSlots,
		        double sellMoodMultiplier, Consumer<Player> onClose) {
			this.viewer             = viewer;
			this.parent             = parent;
			this.definition         = definition;
			this.entry              = entry;
			this.buyPrice           = buyPrice;
			this.handler            = handler;
			this.dropzoneSlots      = dropzoneSlots;
			this.sellMoodMultiplier = sellMoodMultiplier;
			this.onClose            = onClose;
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
