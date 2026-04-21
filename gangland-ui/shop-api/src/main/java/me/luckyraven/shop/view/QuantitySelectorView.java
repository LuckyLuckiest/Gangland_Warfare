package me.luckyraven.shop.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.util.utilities.NumberUtil;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntConsumer;

@RequiredArgsConstructor
public final class QuantitySelectorView {

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

	private static final SoundConfiguration SOUND_ADD        = vanilla("UI_BUTTON_CLICK", 1.5f);
	private static final SoundConfiguration SOUND_SUB        = vanilla("UI_BUTTON_CLICK", 0.8f);
	private static final SoundConfiguration SOUND_MODE_UP    = vanilla("BLOCK_NOTE_BLOCK_HAT", 1.5f);
	private static final SoundConfiguration SOUND_MODE_DOWN  = vanilla("BLOCK_NOTE_BLOCK_HAT", 0.8f);
	private static final SoundConfiguration SOUND_ANVIL_QTY  = vanilla("BLOCK_ANVIL_USE", 1.2f);
	private static final SoundConfiguration SOUND_ANVIL_MODE = vanilla("BLOCK_ANVIL_USE", 1.0f);
	private static final SoundConfiguration SOUND_CONFIRM    = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundConfiguration SOUND_CANCEL     = vanilla("ENTITY_VILLAGER_NO", 1.0f);

	private final JavaPlugin plugin;

	private final Map<Player, QuantitySession> active = new WeakHashMap<>();

	private static SoundConfiguration vanilla(String name, float pitch) {
		return new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, name, 0.6f, pitch);
	}

	/**
	 * Open a picker where {@code staged} means "number of copies to purchase". Each copy costs {@code unitPrice} and
	 * delivers {@code previewItem.getAmount()} items (the template's baked-in stack size). Total = unitPrice × copies.
	 */
	public void open(Player viewer, String title, ItemStack previewItem, BigDecimal unitPrice, int startCopies,
	                 int maxCopies, IntConsumer onConfirm, Runnable onClose) {
		int capped = Math.max(1, maxCopies);
		int staged = Math.clamp(startCopies, 1, capped);

		QuantitySession session = new QuantitySession(previewItem.clone(), unitPrice, capped, staged, onConfirm,
		                                              onClose);
		session.handler = new InventoryHandler(plugin, title, INVENTORY_SIZE, viewer);

		fillGlass(session);
		renderAll(session);

		active.put(viewer, session);
		session.handler.open(viewer);
	}

	public void reopenAfterAnvil(Player viewer) {
		QuantitySession session = active.get(viewer);
		if (session == null) return;

		session.expectingSubview = false;
		renderAll(session);
		session.handler.open(viewer);
	}

	public void handleClose(Player viewer, Inventory inventory) {
		QuantitySession session = active.get(viewer);
		if (session == null) return;
		if (inventory != session.handler.getInventory()) return;
		if (session.expectingSubview) return;

		active.remove(viewer);

		if (session.onClose != null) {
			Bukkit.getScheduler().runTask(plugin, session.onClose);
		}
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void fillGlass(QuantitySession session) {
		ItemStack pane = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem();
		if (pane == null) pane = new ItemStack(Material.STONE);
		ItemBuilder filler = new ItemBuilder(pane).setDisplayName(" ");

		for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
			session.handler.setItem(slot, filler, false, (p, inv, b) -> { });
		}
	}

	private void renderAll(QuantitySession session) {
		renderInfo(session);
		renderItemPreview(session);
		renderAdjustmentButtons(session);
		renderCustomQtyButton(session);
		renderModeRow(session);
		renderConfirmCancel(session);
	}

	private void renderInfo(QuantitySession session) {
		int        itemsPerCopy = Math.max(1, session.item.getAmount());
		int        totalItems   = itemsPerCopy * session.stagedQty;
		BigDecimal totalCost    = session.unitPrice.multiply(BigDecimal.valueOf(session.stagedQty));

		ItemBuilder info = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		info.setDisplayName("&eSelect how many to buy")
		    .setLore("&7Per copy: &f" + itemsPerCopy + " items &7for &6$" + NumberUtil.valueFormat(session.unitPrice),
		             "&7Copies: &f" + session.stagedQty,
		             "&7Items total: &f" + totalItems,
		             "&7Total cost: &6$" + NumberUtil.valueFormat(totalCost), " ",
		             "&8Green adds, red subtracts.", "&8Yellow block = type an exact number of copies.");
		session.handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });
	}

	private void renderItemPreview(QuantitySession session) {
		int        itemsPerCopy = Math.max(1, session.item.getAmount());
		int        totalItems   = itemsPerCopy * session.stagedQty;
		BigDecimal totalCost    = session.unitPrice.multiply(BigDecimal.valueOf(session.stagedQty));

		ItemStack   preview = session.item.clone();
		ItemBuilder builder = new ItemBuilder(preview);
		builder.setLore(
				"&7Per copy: &f" + itemsPerCopy + " items &7for &6$" + NumberUtil.valueFormat(session.unitPrice),
				"&7Copies: &f" + session.stagedQty,
				"&7Items total: &f" + totalItems,
				"&7Total cost: &6$" + NumberUtil.valueFormat(totalCost),
				"&7Step multiplier: &b" + session.mode);
		session.handler.setItem(SLOT_ITEM, builder, false, (p, inv, b) -> { });
	}

	private void renderAdjustmentButtons(QuantitySession session) {
		for (int i = 0; i < GREEN_SLOTS.length; i++) {
			int greenMagnitude = i + 1;
			int greenStep      = greenMagnitude * session.mode;

			ItemBuilder green = new ItemBuilder(material(XMaterial.LIME_CONCRETE, Material.GREEN_WOOL));
			green.setDisplayName("&a+ " + greenStep)
			     .setLore("&7Adds &a" + greenMagnitude + " &7× &b" + session.mode);
			final int greenDelta = greenStep;
			session.handler.setItem(GREEN_SLOTS[i], green, false, (p, inv, b) -> {
				SOUND_ADD.playSound(p);
				adjustQuantity(session, +greenDelta);
			});

			// Mirror the green row around the preview: largest step nearest the item, smallest at the edge.
			int redMagnitude = RED_SLOTS.length - i;
			int redStep      = redMagnitude * session.mode;

			ItemBuilder red = new ItemBuilder(material(XMaterial.RED_CONCRETE, Material.RED_WOOL));
			red.setDisplayName("&c- " + redStep)
			   .setLore("&7Subtracts &c" + redMagnitude + " &7× &b" + session.mode);
			final int redDelta = redStep;
			session.handler.setItem(RED_SLOTS[i], red, false, (p, inv, b) -> {
				SOUND_SUB.playSound(p);
				adjustQuantity(session, -redDelta);
			});
		}
	}

	private void renderCustomQtyButton(QuantitySession session) {
		ItemBuilder button = new ItemBuilder(material(XMaterial.YELLOW_CONCRETE, Material.GOLD_BLOCK));
		button.setDisplayName("&eCustom copies: &f" + session.stagedQty)
		      .setLore("&7Click to type an exact number of copies.", "&8Max: &f" + session.maxQuantity);
		session.handler.setItem(SLOT_QTY_ANVIL, button, false, (p, inv, b) -> {
			SOUND_ANVIL_QTY.playSound(p);
			openQuantityAnvil(p, session);
		});
	}

	private void renderModeRow(QuantitySession session) {
		ItemBuilder down = new ItemBuilder(material(XMaterial.BLUE_CONCRETE, Material.LAPIS_BLOCK));
		down.setDisplayName("&9◄ Previous multiplier").setLore("&7Wraps through 1 to " + MAX_MODE_CYCLE + ".");
		session.handler.setItem(SLOT_MODE_DOWN, down, false, (p, inv, b) -> {
			SOUND_MODE_DOWN.playSound(p);
			cycleMode(session, false);
		});

		ItemBuilder middle = new ItemBuilder(material(XMaterial.MAGENTA_CONCRETE, Material.PURPUR_BLOCK));
		middle.setDisplayName("&dStep multiplier: &b" + session.mode)
		      .setLore("&7Click to type a custom multiplier.", "&8Max: &f" + MAX_MODE_CYCLE);
		session.handler.setItem(SLOT_MODE_ANVIL, middle, false, (p, inv, b) -> {
			SOUND_ANVIL_MODE.playSound(p);
			openModeAnvil(p, session);
		});

		ItemBuilder up = new ItemBuilder(material(XMaterial.BLUE_CONCRETE, Material.LAPIS_BLOCK));
		up.setDisplayName("&9Next multiplier ►").setLore("&7Wraps through 1 to " + MAX_MODE_CYCLE + ".");
		session.handler.setItem(SLOT_MODE_UP, up, false, (p, inv, b) -> {
			SOUND_MODE_UP.playSound(p);
			cycleMode(session, true);
		});
	}

	private void renderConfirmCancel(QuantitySession session) {
		int        itemsPerCopy = Math.max(1, session.item.getAmount());
		int        totalItems   = itemsPerCopy * session.stagedQty;
		BigDecimal totalCost    = session.unitPrice.multiply(BigDecimal.valueOf(session.stagedQty));

		ItemBuilder confirm = new ItemBuilder(material(XMaterial.LIME_WOOL, Material.GREEN_WOOL));
		confirm.setDisplayName("&a&lCONFIRM — &f" + session.stagedQty + " × " + itemsPerCopy + " &afor &6$" +
		                       NumberUtil.valueFormat(totalCost))
		       .setLore("&7Pay &6$" + NumberUtil.valueFormat(totalCost) + " &7and receive &f" + totalItems +
		                " &7items.");
		session.handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> {
			SOUND_CONFIRM.playSound(p);
			confirm(p, session);
		});

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cCANCEL").setLore("&7Discard and go back.");
		session.handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> {
			SOUND_CANCEL.playSound(p);
			cancel(p);
		});
	}

	// ── Actions ──────────────────────────────────────────────────────────

	private void adjustQuantity(QuantitySession session, int delta) {
		session.stagedQty = Math.clamp(session.stagedQty + delta, 1, session.maxQuantity);
		renderInfo(session);
		renderItemPreview(session);
		renderCustomQtyButton(session);
		renderConfirmCancel(session);
	}

	private void cycleMode(QuantitySession session, boolean forward) {
		int current = session.mode;
		int capped  = Math.min(current, MAX_MODE_CYCLE);
		int next;
		if (forward) next = (capped % MAX_MODE_CYCLE) + 1;
		else next = ((capped - 2 + MAX_MODE_CYCLE) % MAX_MODE_CYCLE) + 1;
		session.mode = next;

		renderAdjustmentButtons(session);
		renderModeRow(session);
		renderItemPreview(session);
	}

	private void confirm(Player viewer, QuantitySession session) {
		if (session.onConfirm != null) {
			session.onConfirm.accept(session.stagedQty);
		}
		viewer.closeInventory();
	}

	private void cancel(Player viewer) {
		viewer.closeInventory();
	}

	private void openQuantityAnvil(Player viewer, QuantitySession session) {
		session.expectingSubview = true;

		AnvilGUI.Builder builder = new AnvilGUI.Builder();
		builder.plugin(plugin)
		       .title("Set Quantity")
		       .itemLeft(material(XMaterial.PAPER, Material.PAPER))
		       .text(String.valueOf(session.stagedQty))
		       .onClick((slot, state) -> {
				   if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

				   String raw = state.getText() == null ? "" : state.getText().trim();
				   try {
					   int value = Integer.parseInt(raw);
					   if (value < 1) {
						   viewer.sendMessage(ChatUtil.color("&cCopies must be at least 1."));
						   return Collections.emptyList();
					   }
					   int clamped = Math.min(value, session.maxQuantity);
					   if (value > session.maxQuantity) {
						   viewer.sendMessage(
								   ChatUtil.color("&eCopies capped at &f" + session.maxQuantity + "&e."));
					   }
					   session.stagedQty = clamped;
					   return List.of(AnvilGUI.ResponseAction.close());
				   } catch (NumberFormatException e) {
					   viewer.sendMessage(ChatUtil.color("&cInvalid integer: " + raw));
					   return Collections.emptyList();
				   }
			   })
		       .onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> reopenAfterAnvil(viewer)))
		       .open(viewer);
	}

	private void openModeAnvil(Player viewer, QuantitySession session) {
		session.expectingSubview = true;

		AnvilGUI.Builder builder = new AnvilGUI.Builder();
		builder.plugin(plugin)
		       .title("Set Multiplier")
		       .itemLeft(material(XMaterial.PAPER, Material.PAPER))
		       .text(String.valueOf(session.mode))
		       .onClick((slot, state) -> {
				   if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

				   String raw = state.getText() == null ? "" : state.getText().trim();
				   try {
					   int value = Integer.parseInt(raw);
					   if (value < 1) {
						   viewer.sendMessage(ChatUtil.color("&cMultiplier must be at least 1."));
						   return Collections.emptyList();
					   }
					   session.mode = Math.min(value, MAX_MODE_CYCLE);
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
		       .onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> reopenAfterAnvil(viewer)))
		       .open(viewer);
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	// ── Session ──────────────────────────────────────────────────────────

	static final class QuantitySession {
		final ItemStack   item;
		final BigDecimal  unitPrice;
		final int         maxQuantity;
		final IntConsumer onConfirm;
		final Runnable    onClose;

		InventoryHandler handler;
		int              stagedQty;
		int              mode             = 1;
		boolean          expectingSubview = false;

		QuantitySession(ItemStack item, BigDecimal unitPrice, int maxQuantity, int stagedQty, IntConsumer onConfirm,
		                Runnable onClose) {
			this.item        = item;
			this.unitPrice   = unitPrice;
			this.maxQuantity = maxQuantity;
			this.stagedQty   = stagedQty;
			this.onConfirm   = onConfirm;
			this.onClose     = onClose;
		}
	}

}
