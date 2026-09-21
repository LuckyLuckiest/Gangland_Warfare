package org.luckyraven.gangland.npcshops.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.npcshops.events.trader.TraderBuyRequestEvent;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.keystone.shop.ShopItemEntry;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Quantity-picker panel — decides how many copies of the currently selected {@link ShopItemEntry} to buy. Reads the
 * selected entry + unit price from {@link TraderFlowSession}; stages the copy count and step multiplier on the session
 * so the anvil detour (custom amount / multiplier) survives {@link MenuFlow#suspend} / resume.
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
	private static final int   ROWS            = 6;
	private static final int   MAX_COPIES      = 999;

	private static final SoundEffect SOUND_ADD        = vanilla("UI_BUTTON_CLICK", 1.5f);
	private static final SoundEffect SOUND_SUB        = vanilla("UI_BUTTON_CLICK", 0.8f);
	private static final SoundEffect SOUND_MODE_UP    = vanilla("BLOCK_NOTE_BLOCK_HAT", 1.5f);
	private static final SoundEffect SOUND_MODE_DOWN  = vanilla("BLOCK_NOTE_BLOCK_HAT", 0.8f);
	private static final SoundEffect SOUND_ANVIL_QTY  = vanilla("BLOCK_ANVIL_USE", 1.2f);
	private static final SoundEffect SOUND_ANVIL_MODE = vanilla("BLOCK_ANVIL_USE", 1.0f);
	private static final SoundEffect SOUND_CONFIRM    = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundEffect SOUND_CANCEL     = vanilla("ENTITY_VILLAGER_NO", 1.0f);

	private final JavaPlugin plugin;

	private static SoundEffect vanilla(String name, float pitch) {
		return new SoundEffect(SoundEffect.SoundType.VANILLA, name, 0.6f, pitch);
	}

	@Override
	public int rows(TraderFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(TraderFlowSession session) {
		return "&8Buy Amount&r &8&l[&b&l" + session.trait.displayName() + "&8&l]";
	}

	@Override
	public void render(MenuFlow<TraderFlowSession> flow, ChestMenuBuilder builder, TraderFlowSession session) {
		if (session.selectedEntry == null) {
			flow.back();
			return;
		}
		if (session.quantityStaged < 1) session.quantityStaged = 1;
		if (session.quantityStaged > MAX_COPIES) session.quantityStaged = MAX_COPIES;
		if (session.quantityMode < 1) session.quantityMode = 1;
		if (session.quantityMode > MAX_MODE_CYCLE) session.quantityMode = MAX_MODE_CYCLE;

		fillGlass(builder);
		renderInfo(builder, session);
		renderItemPreview(builder, session);
		renderAdjustmentButtons(flow, builder, session);
		renderCustomQtyButton(flow, builder, session);
		renderModeRow(flow, builder, session);
		renderConfirmCancel(flow, builder, session);
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private BigDecimal unitPrice(TraderFlowSession session) {
		return session.basePrice.multiply(BigDecimal.valueOf(session.moodMultiplier));
	}

	private void fillGlass(ChestMenuBuilder builder) {
		ItemStack pane = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem();
		if (pane == null) pane = new ItemStack(Material.STONE);
		builder.fill(FillComponent.of(pane.getType()).name(" "));
	}

	private void renderInfo(ChestMenuBuilder builder, TraderFlowSession session) {
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
		builder.slot(SLOT_INFO, ItemComponent.of(info));
	}

	private void renderItemPreview(ChestMenuBuilder builder, TraderFlowSession session) {
		ItemStack  item         = session.selectedEntry.getItem();
		int        itemsPerCopy = Math.max(1, item.getAmount());
		int        totalItems   = itemsPerCopy * session.quantityStaged;
		BigDecimal unit         = unitPrice(session);
		BigDecimal totalCost    = unit.multiply(BigDecimal.valueOf(session.quantityStaged));

		ItemBuilder preview = new ItemBuilder(item.clone());
		preview.setLore("&7Per copy: &f" + itemsPerCopy + " items &7for &6$" + NumberUtil.valueFormat(unit),
		                "&7Copies: &f" + session.quantityStaged, "&7Items total: &f" + totalItems,
		                "&7Total cost: &6$" + NumberUtil.valueFormat(totalCost),
		                "&7Step multiplier: &b" + session.quantityMode);
		builder.slot(SLOT_ITEM, ItemComponent.of(preview));
	}

	private void renderAdjustmentButtons(MenuFlow<TraderFlowSession> flow, ChestMenuBuilder builder,
	                                     TraderFlowSession session) {
		for (int i = 0; i < GREEN_SLOTS.length; i++) {
			int greenMagnitude = i + 1;
			int greenStep      = greenMagnitude * session.quantityMode;

			ItemBuilder green = new ItemBuilder(material(XMaterial.LIME_CONCRETE, Material.GREEN_WOOL));
			green.setDisplayName("&a+ " + greenStep)
			     .setLore("&7Adds &a" + greenMagnitude + " &7× &b" + session.quantityMode);
			final int greenDelta = greenStep;
			builder.slot(GREEN_SLOTS[i], ItemComponent.of(green).onAnyClick(ctx -> {
				adjustQuantity(flow, session, +greenDelta);
				Bukkit.getScheduler().runTask(plugin, () -> SOUND_ADD.playSound(ctx.player()));
			}));

			int redMagnitude = RED_SLOTS.length - i;
			int redStep      = redMagnitude * session.quantityMode;

			ItemBuilder red = new ItemBuilder(material(XMaterial.RED_CONCRETE, Material.RED_WOOL));
			red.setDisplayName("&c- " + redStep)
			   .setLore("&7Subtracts &c" + redMagnitude + " &7× &b" + session.quantityMode);
			final int redDelta = redStep;
			builder.slot(RED_SLOTS[i], ItemComponent.of(red).onAnyClick(ctx -> {
				adjustQuantity(flow, session, -redDelta);
				Bukkit.getScheduler().runTask(plugin, () -> SOUND_SUB.playSound(ctx.player()));
			}));
		}
	}

	private void renderCustomQtyButton(MenuFlow<TraderFlowSession> flow, ChestMenuBuilder builder,
	                                   TraderFlowSession session) {
		ItemBuilder button = new ItemBuilder(material(XMaterial.YELLOW_CONCRETE, Material.GOLD_BLOCK));
		button.setDisplayName("&eCustom copies: &f" + session.quantityStaged)
		      .setLore("&7Click to type an exact number of copies.", "&8Max: &f" + MAX_COPIES);
		builder.slot(SLOT_QTY_ANVIL, ItemComponent.of(button).onAnyClick(ctx -> {
			openQuantityAnvil(flow, ctx.player(), session);
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_ANVIL_QTY.playSound(ctx.player()));
		}));
	}

	private void renderModeRow(MenuFlow<TraderFlowSession> flow, ChestMenuBuilder builder, TraderFlowSession session) {
		ItemBuilder down = new ItemBuilder(material(XMaterial.BLUE_CONCRETE, Material.LAPIS_BLOCK));
		down.setDisplayName("&9◄ Previous multiplier").setLore("&7Wraps through 1 to " + MAX_MODE_CYCLE + ".");
		builder.slot(SLOT_MODE_DOWN, ItemComponent.of(down).onAnyClick(ctx -> {
			cycleMode(flow, session, false);
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_MODE_DOWN.playSound(ctx.player()));
		}));

		ItemBuilder middle = new ItemBuilder(material(XMaterial.MAGENTA_CONCRETE, Material.PURPUR_BLOCK));
		middle.setDisplayName("&dStep multiplier: &b" + session.quantityMode)
		      .setLore("&7Click to type a custom multiplier.", "&8Max: &f" + MAX_MODE_CYCLE);
		builder.slot(SLOT_MODE_ANVIL, ItemComponent.of(middle).onAnyClick(ctx -> {
			openModeAnvil(flow, ctx.player(), session);
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_ANVIL_MODE.playSound(ctx.player()));
		}));

		ItemBuilder up = new ItemBuilder(material(XMaterial.BLUE_CONCRETE, Material.LAPIS_BLOCK));
		up.setDisplayName("&9Next multiplier ►").setLore("&7Wraps through 1 to " + MAX_MODE_CYCLE + ".");
		builder.slot(SLOT_MODE_UP, ItemComponent.of(up).onAnyClick(ctx -> {
			cycleMode(flow, session, true);
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_MODE_UP.playSound(ctx.player()));
		}));
	}

	private void renderConfirmCancel(MenuFlow<TraderFlowSession> flow, ChestMenuBuilder builder,
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
		builder.slot(SLOT_CONFIRM, ItemComponent.of(confirm).onAnyClick(ctx -> confirm(flow, ctx.player(), session)));

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cCANCEL").setLore("&7Discard and go back.");
		builder.slot(SLOT_CANCEL, ItemComponent.of(cancel).onAnyClick(ctx -> {
			flow.back();
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_CANCEL.playSound(ctx.player()));
		}));
	}

	// ── Actions ──────────────────────────────────────────────────────────

	private void adjustQuantity(MenuFlow<TraderFlowSession> flow, TraderFlowSession session, int delta) {
		session.quantityStaged = Math.max(1, Math.min(session.quantityStaged + delta, MAX_COPIES));
		flow.rerender();
	}

	private void cycleMode(MenuFlow<TraderFlowSession> flow, TraderFlowSession session, boolean forward) {
		int current = session.quantityMode;
		int capped  = Math.min(current, MAX_MODE_CYCLE);
		int next;
		if (forward) next = (capped % MAX_MODE_CYCLE) + 1;
		else next = ((capped - 2 + MAX_MODE_CYCLE) % MAX_MODE_CYCLE) + 1;
		session.quantityMode = next;
		flow.rerender();
	}

	private void confirm(MenuFlow<TraderFlowSession> flow, Player viewer, TraderFlowSession session) {
		int        copies = session.quantityStaged;
		BigDecimal total  = unitPrice(session).multiply(BigDecimal.valueOf(copies));

		TraderBuyRequestEvent event = new TraderBuyRequestEvent(viewer, session.trader, session.selectedEntry, total,
		                                                        copies);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			flow.back();
			return;
		}

		// Reset picker state on successful commit so the next entry starts fresh.
		session.quantityStaged = 1;
		session.quantityMode   = 1;
		flow.end();
		Bukkit.getScheduler().runTask(plugin, () -> SOUND_CONFIRM.playSound(viewer));
	}

	private void openQuantityAnvil(MenuFlow<TraderFlowSession> flow, Player viewer, TraderFlowSession session) {
		flow.suspend();
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
								  flow.resume();
								  flow.switchTo(TraderFlowSession.PANEL_QUANTITY);
							  }))
		                      .open(viewer);
	}

	private void openModeAnvil(MenuFlow<TraderFlowSession> flow, Player viewer, TraderFlowSession session) {
		flow.suspend();
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
								  flow.resume();
								  flow.switchTo(TraderFlowSession.PANEL_QUANTITY);
							  }))
		                      .open(viewer);
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

}
