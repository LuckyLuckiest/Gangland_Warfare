package org.luckyraven.gangland.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.events.trader.TraderBuyRequestEvent;
import org.luckyraven.gangland.core.ItemBuilder;
import org.luckyraven.gangland.core.configuration.SoundConfiguration;
import org.luckyraven.gangland.core.utilities.ChatUtil;
import org.luckyraven.gangland.core.utilities.NumberUtil;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.inventory.flow.Panel;
import org.luckyraven.gangland.shop.ShopItemEntry;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Quantity-picker panel — decides how many copies of the currently selected {@link ShopItemEntry} to buy. Reads the
 * selected entry + unit price from {@link TraderFlowSession}; stages the copy count and step multiplier on the session
 * so the anvil detour (custom amount / multiplier) survives {@link MultiPanelInventory#suspend} / resume.
 *
 * <p>Formerly in shop-api; moved into cops-n-crooks alongside the other trader panels so it can be a direct
 * {@code Panel<TraderFlowSession>} without cross-module generic variance.
 */
@RequiredArgsConstructor
public final class QuantitySelectorView implements Panel<TraderFlowSession> {

	private static final int   SLOT_INFO       = 4;
	private static final int   SLOT_ITEM       = 22;
	private static final int   SLOT_QTY_ANVIL  = 31;
	private static final int   SLOT_MODE_DOWN  = 38;
	private static final int   SLOT_MODE_ANVIL = 40;
	private static final int   SLOT_MODE_UP    = 42;
	private static final int   SLOT_CONFIRM    = 48;
	private static final int   SLOT_CANCEL     = 50;
	private static final int[] GREEN_SLOTS     = {18, 19, 20, 21};
	private static final int[] RED_SLOTS       = {23, 24, 25, 26};
	private static final int   MAX_MODE_CYCLE  = 8;
	private static final int   INVENTORY_SIZE  = 54;
	private static final int   MAX_COPIES      = 999;

	private static final SoundConfiguration SOUND_ADD        = vanilla("UI_BUTTON_CLICK", 1.5f);
	private static final SoundConfiguration SOUND_SUB        = vanilla("UI_BUTTON_CLICK", 0.8f);
	private static final SoundConfiguration SOUND_MODE_UP    = vanilla("BLOCK_NOTE_BLOCK_HAT", 1.5f);
	private static final SoundConfiguration SOUND_MODE_DOWN  = vanilla("BLOCK_NOTE_BLOCK_HAT", 0.8f);
	private static final SoundConfiguration SOUND_ANVIL_QTY  = vanilla("BLOCK_ANVIL_USE", 1.2f);
	private static final SoundConfiguration SOUND_ANVIL_MODE = vanilla("BLOCK_ANVIL_USE", 1.0f);
	private static final SoundConfiguration SOUND_CONFIRM    = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundConfiguration SOUND_CANCEL     = vanilla("ENTITY_VILLAGER_NO", 1.0f);

	private final JavaPlugin plugin;

	private static SoundConfiguration vanilla(String name, float pitch) {
		return new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, name, 0.6f, pitch);
	}

	@Override
	public int size(TraderFlowSession session) {
		return INVENTORY_SIZE;
	}

	@Override
	public String title(TraderFlowSession session) {
		return "&8Buy Amount&r &8&l[&b&l" + session.trait.displayName() + "&8&l]";
	}

	@Override
	public void render(MultiPanelInventory<TraderFlowSession> host, InventoryHandler handler, Player viewer,
	                   TraderFlowSession session) {
		if (session.selectedEntry == null) {
			host.back();
			return;
		}
		if (session.quantityStaged < 1) session.quantityStaged = 1;
		if (session.quantityStaged > MAX_COPIES) session.quantityStaged = MAX_COPIES;
		if (session.quantityMode < 1) session.quantityMode = 1;
		if (session.quantityMode > MAX_MODE_CYCLE) session.quantityMode = MAX_MODE_CYCLE;

		fillGlass(handler);
		renderInfo(handler, session);
		renderItemPreview(handler, session);
		renderAdjustmentButtons(host, handler, session);
		renderCustomQtyButton(host, handler, session);
		renderModeRow(host, handler, session);
		renderConfirmCancel(host, handler, session);
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private BigDecimal unitPrice(TraderFlowSession session) {
		return session.basePrice.multiply(BigDecimal.valueOf(session.moodMultiplier));
	}

	private void fillGlass(InventoryHandler handler) {
		ItemStack pane = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem();
		if (pane == null) pane = new ItemStack(Material.STONE);
		ItemBuilder filler = new ItemBuilder(pane).setDisplayName(" ");
		for (int slot = 0; slot < INVENTORY_SIZE; slot++) handler.setItem(slot, filler, false, (p, inv, b) -> { });
	}

	private void renderInfo(InventoryHandler handler, TraderFlowSession session) {
		ItemStack  item         = session.selectedEntry.getItem();
		int        itemsPerCopy = Math.max(1, item.getAmount());
		int        totalItems   = itemsPerCopy * session.quantityStaged;
		BigDecimal unit         = unitPrice(session);
		BigDecimal totalCost    = unit.multiply(BigDecimal.valueOf(session.quantityStaged));

		ItemBuilder info = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		info.setDisplayName("&eSelect how many to buy")
		    .setLore("&7Per copy: &f" + itemsPerCopy + " items &7for &6$" + NumberUtil.valueFormat(unit),
		             "&7Copies: &f" + session.quantityStaged, "&7Items total: &f" + totalItems,
		             "&7Total cost: &6$" + NumberUtil.valueFormat(totalCost), " ", "&8Green adds, red subtracts.",
		             "&8Yellow block = type an exact number of copies.");
		handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });
	}

	private void renderItemPreview(InventoryHandler handler, TraderFlowSession session) {
		ItemStack  item         = session.selectedEntry.getItem();
		int        itemsPerCopy = Math.max(1, item.getAmount());
		int        totalItems   = itemsPerCopy * session.quantityStaged;
		BigDecimal unit         = unitPrice(session);
		BigDecimal totalCost    = unit.multiply(BigDecimal.valueOf(session.quantityStaged));

		ItemBuilder builder = new ItemBuilder(item.clone());
		builder.setLore("&7Per copy: &f" + itemsPerCopy + " items &7for &6$" + NumberUtil.valueFormat(unit),
		                "&7Copies: &f" + session.quantityStaged, "&7Items total: &f" + totalItems,
		                "&7Total cost: &6$" + NumberUtil.valueFormat(totalCost),
		                "&7Step multiplier: &b" + session.quantityMode);
		handler.setItem(SLOT_ITEM, builder, false, (p, inv, b) -> { });
	}

	private void renderAdjustmentButtons(MultiPanelInventory<TraderFlowSession> host, InventoryHandler handler,
	                                     TraderFlowSession session) {
		for (int i = 0; i < GREEN_SLOTS.length; i++) {
			int greenMagnitude = i + 1;
			int greenStep      = greenMagnitude * session.quantityMode;

			ItemBuilder green = new ItemBuilder(material(XMaterial.LIME_CONCRETE, Material.GREEN_WOOL));
			green.setDisplayName("&a+ " + greenStep)
			     .setLore("&7Adds &a" + greenMagnitude + " &7× &b" + session.quantityMode);
			final int greenDelta = greenStep;
			handler.setItem(GREEN_SLOTS[i], green, false, (p, inv, b) -> {
				adjustQuantity(host, session, +greenDelta);
				Bukkit.getScheduler().runTask(plugin, () -> SOUND_ADD.playSound(p));
			});

			int redMagnitude = RED_SLOTS.length - i;
			int redStep      = redMagnitude * session.quantityMode;

			ItemBuilder red = new ItemBuilder(material(XMaterial.RED_CONCRETE, Material.RED_WOOL));
			red.setDisplayName("&c- " + redStep)
			   .setLore("&7Subtracts &c" + redMagnitude + " &7× &b" + session.quantityMode);
			final int redDelta = redStep;
			handler.setItem(RED_SLOTS[i], red, false, (p, inv, b) -> {
				adjustQuantity(host, session, -redDelta);
				Bukkit.getScheduler().runTask(plugin, () -> SOUND_SUB.playSound(p));
			});
		}
	}

	private void renderCustomQtyButton(MultiPanelInventory<TraderFlowSession> host, InventoryHandler handler,
	                                   TraderFlowSession session) {
		ItemBuilder button = new ItemBuilder(material(XMaterial.YELLOW_CONCRETE, Material.GOLD_BLOCK));
		button.setDisplayName("&eCustom copies: &f" + session.quantityStaged)
		      .setLore("&7Click to type an exact number of copies.", "&8Max: &f" + MAX_COPIES);
		handler.setItem(SLOT_QTY_ANVIL, button, false, (p, inv, b) -> {
			openQuantityAnvil(host, p, session);
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_ANVIL_QTY.playSound(p));
		});
	}

	private void renderModeRow(MultiPanelInventory<TraderFlowSession> host, InventoryHandler handler,
	                           TraderFlowSession session) {
		ItemBuilder down = new ItemBuilder(material(XMaterial.BLUE_CONCRETE, Material.LAPIS_BLOCK));
		down.setDisplayName("&9◄ Previous multiplier").setLore("&7Wraps through 1 to " + MAX_MODE_CYCLE + ".");
		handler.setItem(SLOT_MODE_DOWN, down, false, (p, inv, b) -> {
			cycleMode(host, session, false);
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_MODE_DOWN.playSound(p));
		});

		ItemBuilder middle = new ItemBuilder(material(XMaterial.MAGENTA_CONCRETE, Material.PURPUR_BLOCK));
		middle.setDisplayName("&dStep multiplier: &b" + session.quantityMode)
		      .setLore("&7Click to type a custom multiplier.", "&8Max: &f" + MAX_MODE_CYCLE);
		handler.setItem(SLOT_MODE_ANVIL, middle, false, (p, inv, b) -> {
			openModeAnvil(host, p, session);
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_ANVIL_MODE.playSound(p));
		});

		ItemBuilder up = new ItemBuilder(material(XMaterial.BLUE_CONCRETE, Material.LAPIS_BLOCK));
		up.setDisplayName("&9Next multiplier ►").setLore("&7Wraps through 1 to " + MAX_MODE_CYCLE + ".");
		handler.setItem(SLOT_MODE_UP, up, false, (p, inv, b) -> {
			cycleMode(host, session, true);
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_MODE_UP.playSound(p));
		});
	}

	private void renderConfirmCancel(MultiPanelInventory<TraderFlowSession> host, InventoryHandler handler,
	                                 TraderFlowSession session) {
		ItemStack  item         = session.selectedEntry.getItem();
		int        itemsPerCopy = Math.max(1, item.getAmount());
		int        totalItems   = itemsPerCopy * session.quantityStaged;
		BigDecimal totalCost    = unitPrice(session).multiply(BigDecimal.valueOf(session.quantityStaged));

		ItemBuilder confirm = new ItemBuilder(material(XMaterial.LIME_WOOL, Material.GREEN_WOOL));
		confirm.setDisplayName("&a&lCONFIRM — &f" + session.quantityStaged + " × " + itemsPerCopy + " &afor &6$" +
		                       NumberUtil.valueFormat(totalCost))
		       .setLore("&7Pay &6$" + NumberUtil.valueFormat(totalCost) + " &7and receive &f" + totalItems +
		                " &7items.");
		handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> confirm(host, p, session));

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cCANCEL").setLore("&7Discard and go back.");
		handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> {
			host.back();
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_CANCEL.playSound(p));
		});
	}

	// ── Actions ──────────────────────────────────────────────────────────

	private void adjustQuantity(MultiPanelInventory<TraderFlowSession> host, TraderFlowSession session, int delta) {
		session.quantityStaged = Math.clamp(session.quantityStaged + delta, 1, MAX_COPIES);
		host.rerender();
	}

	private void cycleMode(MultiPanelInventory<TraderFlowSession> host, TraderFlowSession session, boolean forward) {
		int current = session.quantityMode;
		int capped  = Math.min(current, MAX_MODE_CYCLE);
		int next;
		if (forward) next = (capped % MAX_MODE_CYCLE) + 1;
		else next = ((capped - 2 + MAX_MODE_CYCLE) % MAX_MODE_CYCLE) + 1;
		session.quantityMode = next;
		host.rerender();
	}

	private void confirm(MultiPanelInventory<TraderFlowSession> host, Player viewer, TraderFlowSession session) {
		int        copies = session.quantityStaged;
		BigDecimal total  = unitPrice(session).multiply(BigDecimal.valueOf(copies));

		TraderBuyRequestEvent event = new TraderBuyRequestEvent(viewer, session.trader, session.selectedEntry, total,
		                                                        copies);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			host.back();
			return;
		}

		// Reset picker state on successful commit so the next entry starts fresh.
		session.quantityStaged = 1;
		session.quantityMode   = 1;
		host.end();
		Bukkit.getScheduler().runTask(plugin, () -> SOUND_CONFIRM.playSound(viewer));
	}

	private void openQuantityAnvil(MultiPanelInventory<TraderFlowSession> host, Player viewer,
	                               TraderFlowSession session) {
		host.suspend();
		new AnvilGUI.Builder().plugin(plugin)
		                      .title("Set Quantity")
		                      .itemLeft(material(XMaterial.PAPER, Material.PAPER))
		                      .text(String.valueOf(session.quantityStaged))
		                      .onClick((slot, state) -> {
								  if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
								  String raw = state.getText() == null ? "" : state.getText().trim();
								  try {
									  int value = Integer.parseInt(raw);
									  if (value < 1) {
										  viewer.sendMessage(ChatUtil.color("&cCopies must be at least 1."));
										  return Collections.emptyList();
									  }
									  int clamped = Math.min(value, MAX_COPIES);
									  if (value > MAX_COPIES) {
										  viewer.sendMessage(
												  ChatUtil.color("&eCopies capped at &f" + MAX_COPIES + "&e."));
									  }
									  session.quantityStaged = clamped;
									  return List.of(AnvilGUI.ResponseAction.close());
								  } catch (NumberFormatException e) {
									  viewer.sendMessage(ChatUtil.color("&cInvalid integer: " + raw));
									  return Collections.emptyList();
								  }
							  })
		                      .onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> {
								  host.resume();
								  host.switchTo(TraderFlowSession.PANEL_QUANTITY);
							  }))
		                      .open(viewer);
	}

	private void openModeAnvil(MultiPanelInventory<TraderFlowSession> host, Player viewer, TraderFlowSession session) {
		host.suspend();
		new AnvilGUI.Builder().plugin(plugin)
		                      .title("Set Multiplier")
		                      .itemLeft(material(XMaterial.PAPER, Material.PAPER))
		                      .text(String.valueOf(session.quantityMode))
		                      .onClick((slot, state) -> {
								  if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
								  String raw = state.getText() == null ? "" : state.getText().trim();
								  try {
									  int value = Integer.parseInt(raw);
									  if (value < 1) {
										  viewer.sendMessage(ChatUtil.color("&cMultiplier must be at least 1."));
										  return Collections.emptyList();
									  }
									  session.quantityMode = Math.min(value, MAX_MODE_CYCLE);
									  if (value > MAX_MODE_CYCLE) {
										  viewer.sendMessage(
												  ChatUtil.color("&eMultiplier capped at &f" + MAX_MODE_CYCLE + "&e."));
									  }
									  return List.of(AnvilGUI.ResponseAction.close());
								  } catch (NumberFormatException e) {
									  viewer.sendMessage(ChatUtil.color("&cInvalid integer: " + raw));
									  return Collections.emptyList();
								  }
							  })
		                      .onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> {
								  host.resume();
								  host.switchTo(TraderFlowSession.PANEL_QUANTITY);
							  }))
		                      .open(viewer);
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

}
