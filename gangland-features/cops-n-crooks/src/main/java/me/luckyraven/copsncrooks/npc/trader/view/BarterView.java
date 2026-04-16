package me.luckyraven.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.events.trader.TraderBarterEvent;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.ShopItemEntry;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.valuation.CategoryBarterValuator;
import me.luckyraven.shop.valuation.ItemValuation;
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

/**
 * Drop-zone view for the trader barter flow. The player drops items from any of the shop's
 * {@link me.luckyraven.shop.BarterCategory barter categories}; their combined value (via
 * {@link CategoryBarterValuator}) must meet or exceed the negotiated asking price for the swap to confirm. <strong>No
 * economy money is involved at any point</strong>.
 */
@RequiredArgsConstructor
public final class BarterView implements BeanLifecycle {

	private static final int SIZE         = 54;
	private static final int SLOT_PINNED  = 4;
	private static final int SLOT_OFFER   = 26;
	private static final int SLOT_BACK    = 45;
	private static final int SLOT_CLEAR   = 48;
	private static final int SLOT_CONFIRM = 50;
	private static final int SLOT_CANCEL  = 53;

	private static final int[] ALL_DROPZONE_SLOTS = {18, 19, 20, 21, 22, 23, 24, 27, 28, 29, 30, 31, 32, 33};

	private static final SoundConfiguration SOUND_CONFIRM = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundConfiguration SOUND_CANCEL  = vanilla("ENTITY_VILLAGER_NO", 1.0f);
	private static final SoundConfiguration SOUND_CLICK   = vanilla("UI_BUTTON_CLICK", 1.0f);

	private final JavaPlugin             plugin;
	private final MoodService            moodService;
	private final CategoryBarterValuator valuator;
	private final ItemRefresherRegistry  refresherRegistry;
	private final ShopDisplayResolver    displayResolver;
	private final TraderSettings         settings;

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
		List<Player> viewers = new ArrayList<>(active.keySet());
		for (Player viewer : viewers) {
			Session session = active.remove(viewer);
			if (session == null) continue;
			session.committed           = true;
			session.returnToNegotiation = false;
			returnItemsToPlayer(viewer, session);
			try {
				viewer.closeInventory();
			} catch (Exception ignored) {
			}
		}
	}

	public void open(Player viewer, NegotiationView.NegotiationSession parent, ShopDefinition def, double askingValue,
	                 Consumer<Player> onClose) {
		double mood = moodService.priceMultiplier(parent.getTrader().getData().getId(), viewer.getUniqueId(),
		                                          parent.getTrait().profile());
		double barterMood = 2.0 - mood;

		int[] slots = dropzoneSlots(settings.getSellMaxOfferSlots());

		ItemStack        decorated = refresherRegistry.decorate(parent.getEntry().getItem(), viewer);
		String           label     = displayResolver.cleanDisplayName(decorated);
		InventoryHandler handler   = new InventoryHandler(plugin, "&8Barter for " + label, SIZE, viewer);

		Session session = new Session(viewer, parent, def, parent.getEntry(), askingValue, handler, slots, barterMood,
		                              onClose);
		active.put(viewer, session);

		for (int slot : slots) {
			handler.setItem(slot, null, true);
		}

		renderChrome(session);

		handler.open(viewer);
	}

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
		session.handler.getInventory().setItem(SLOT_PINNED, null);
		session.handler.getInventory().setItem(SLOT_OFFER, null);
		session.handler.getInventory().setItem(SLOT_BACK, null);
		session.handler.getInventory().setItem(SLOT_CANCEL, null);
		session.handler.getInventory().setItem(SLOT_CLEAR, null);
		session.handler.getInventory().setItem(SLOT_CONFIRM, null);

		renderPinned(session);
		renderOffer(session);
		renderBack(session);
		renderCancel(session);
		renderClear(session);
		renderConfirm(session);

		// Dropzone preservation trick — keep slots empty during background fill.
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

	private void renderPinned(Session session) {
		ItemStack   decorated = refresherRegistry.decorate(session.entry.getItem(), session.viewer);
		ItemBuilder pinned    = new ItemBuilder(decorated.clone());
		pinned.setDisplayName(displayResolver.cleanDisplayName(decorated))
		      .setLore("&7Asking value: &6$" + NumberUtil.valueFormat(session.askingValue),
		               "&7Trait: &d" + session.parent.getTrait().displayName(),
		               "&7Mood: " + moodLabel(session.barterMoodMultiplier),
		               " ",
		               "&8Drop barter items below to swap.");
		session.handler.setItem(SLOT_PINNED, pinned, false, (p, inv, b) -> { });
	}

	private void renderOffer(Session session) {
		recomputeOffer(session);

		boolean ready  = session.offeredValue >= session.askingValue;
		double  needed = Math.max(0.0, session.askingValue - session.offeredValue);
		double  excess = Math.max(0.0, session.offeredValue - session.askingValue);

		ItemStack icon;
		if (session.offeredValue <= 0.0) {
			icon = material(XMaterial.GOLD_NUGGET, Material.GOLD_NUGGET);
		} else if (!ready) {
			icon = material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT);
		} else {
			icon = material(XMaterial.EMERALD, Material.EMERALD);
		}

		ItemBuilder  offer = new ItemBuilder(icon).setDisplayName("&6&lBARTER OFFER");
		List<String> lore  = new ArrayList<>();
		if (session.offeredValue <= 0.0) {
			lore.add("&7Drop barter items on the left.");
		} else if (!ready) {
			lore.add("&7Asking:   &6$" + NumberUtil.valueFormat(session.askingValue));
			lore.add("&7Offered:  &e$" + NumberUtil.valueFormat(session.offeredValue));
			lore.add("&cNeed:     &c$" + NumberUtil.valueFormat(needed) + " more");
		} else {
			lore.add("&7Asking:   &6$" + NumberUtil.valueFormat(session.askingValue));
			lore.add("&aOffered:  &a$" + NumberUtil.valueFormat(session.offeredValue));
			lore.add("&7Status:   &a&lREADY TO TRADE");
			if (excess > 0.0) {
				lore.add("&8(overpay $" + NumberUtil.valueFormat(excess) + " forfeited)");
			}
		}
		lore.add(" ");
		if (session.breakdown.isEmpty()) {
			lore.add("&8No items dropped yet.");
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
		boolean ready = session.offeredValue >= session.askingValue && session.offeredValue > 0.0;

		ItemStack icon = ready ? material(XMaterial.LIME_WOOL, Material.GREEN_WOOL)
		                       : material(XMaterial.GRAY_WOOL, Material.GRAY_WOOL);

		String displayName = ready ? "&aCONFIRM &7swap" : "&8Need more value";

		ItemBuilder confirm = new ItemBuilder(icon);
		confirm.setDisplayName(displayName)
		       .setLore(ready ? "&7Hand over your items and receive the trader's." :
		                "&7Drop barter items worth at least &6$" + NumberUtil.valueFormat(session.askingValue) + "&7.");
		session.handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> onConfirm(p, session));
	}

	private void recomputeOffer(Session session) {
		double       offered   = 0.0;
		List<String> breakdown = new ArrayList<>();

		for (int slot : session.dropzoneSlots) {
			ItemStack rawStack = session.handler.getInventory().getItem(slot);
			if (rawStack == null || rawStack.getType() == Material.AIR) {
				continue;
			}
			ItemStack decorated = refresherRegistry.decorate(rawStack, session.viewer);
			ItemValuation valuation = valuator.value(session.definition, decorated,
			                                         session.parent.getTrait().profile().barterPriceRatio(),
			                                         session.barterMoodMultiplier);
			String label = displayResolver.cleanDisplayName(decorated);
			if (valuation.hasValue()) {
				double lineTotal = valuation.unitPrice() * decorated.getAmount();
				offered += lineTotal;
				breakdown.add(
						"&7" + label + " x" + decorated.getAmount() + " &8→ &a$" + NumberUtil.valueFormat(lineTotal));
			} else {
				breakdown.add("&8" + label + " x" + decorated.getAmount() + " &8→ &cnot accepted");
			}
		}

		session.offeredValue = offered;
		session.breakdown    = breakdown;
	}

	private String moodLabel(double barterMoodMultiplier) {
		// barterMood = 2.0 - mood: friendly mood (≤0.95) → barter ≥1.05 (generous); hostile mood (≥1.25) → ≤0.75.
		if (barterMoodMultiplier >= 1.05) return "&aFriendly";
		if (barterMoodMultiplier > 0.95) return "&fNeutral";
		if (barterMoodMultiplier > 0.75) return "&eWary";
		return "&cHostile";
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
		recomputeOffer(session);
		renderChrome(session);
	}

	private void onConfirm(Player viewer, Session session) {
		if (session.offeredValue < session.askingValue || session.offeredValue <= 0.0) {
			return;
		}

		List<ItemStack> offered = collectOfferedItems(session);

		TraderBarterEvent event = new TraderBarterEvent(viewer, session.parent.getTrader(), session.entry,
		                                                session.askingValue, session.offeredValue, offered);
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

	static final class Session {
		final Player                             viewer;
		final NegotiationView.NegotiationSession parent;
		final ShopDefinition                     definition;
		final ShopItemEntry                      entry;
		final double                             askingValue;
		final InventoryHandler                   handler;
		final int[]                              dropzoneSlots;
		final double                             barterMoodMultiplier;
		final Consumer<Player>                   onClose;

		double       offeredValue = 0.0;
		List<String> breakdown    = new ArrayList<>();

		boolean committed           = false;
		boolean returnToNegotiation = true;

		Session(Player viewer, NegotiationView.NegotiationSession parent, ShopDefinition definition,
		        ShopItemEntry entry, double askingValue, InventoryHandler handler, int[] dropzoneSlots,
		        double barterMoodMultiplier, Consumer<Player> onClose) {
			this.viewer               = viewer;
			this.parent               = parent;
			this.definition           = definition;
			this.entry                = entry;
			this.askingValue          = askingValue;
			this.handler              = handler;
			this.dropzoneSlots        = dropzoneSlots;
			this.barterMoodMultiplier = barterMoodMultiplier;
			this.onClose              = onClose;
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
