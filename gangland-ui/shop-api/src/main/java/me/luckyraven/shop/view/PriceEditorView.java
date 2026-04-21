package me.luckyraven.shop.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.shop.config.ShopUiSettings;
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
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class PriceEditorView {

	private static final int   SLOT_INFO        = 4;
	private static final int   SLOT_ITEM        = 22;
	private static final int   SLOT_PRICE_ANVIL = 31;
	private static final int   SLOT_MODE_DOWN   = 38;
	private static final int   SLOT_MODE_ANVIL  = 40;
	private static final int   SLOT_MODE_UP     = 42;
	private static final int   SLOT_SAVE        = 48;
	private static final int   SLOT_CANCEL      = 50;
	private static final int[] GREEN_SLOTS      = {18, 19, 20, 21};
	private static final int[] RED_SLOTS        = {23, 24, 25, 26};
	private static final int   MAX_MODE_CYCLE   = 10;
	private static final int   INVENTORY_SIZE   = 54;

	private static final SoundConfiguration              SOUND_ADD         = vanilla("UI_BUTTON_CLICK", 1.5f);
	private static final SoundConfiguration              SOUND_SUB         = vanilla("UI_BUTTON_CLICK", 0.8f);
	private static final SoundConfiguration              SOUND_MODE_UP     = vanilla("BLOCK_NOTE_BLOCK_HAT", 1.5f);
	private static final SoundConfiguration              SOUND_MODE_DOWN   = vanilla("BLOCK_NOTE_BLOCK_HAT", 0.8f);
	private static final SoundConfiguration              SOUND_ANVIL_PRICE = vanilla("BLOCK_ANVIL_USE", 1.2f);
	private static final SoundConfiguration              SOUND_ANVIL_MODE  = vanilla("BLOCK_ANVIL_USE", 1.0f);
	private static final SoundConfiguration              SOUND_SAVE        = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundConfiguration              SOUND_CANCEL      = vanilla("ENTITY_VILLAGER_NO", 1.0f);
	private final        JavaPlugin                      plugin;
	private final        ShopUiSettings                  settings;
	private final        Map<Player, PriceEditorSession> active            = new WeakHashMap<>();

	private static SoundConfiguration vanilla(String name, float pitch) {
		return new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, name, 0.6f, pitch);
	}

	public void open(Player admin, ShopAdminView.Session parentSession, int editedSlot, ItemStack item,
	                 BigDecimal originalPrice, Runnable reopenCallback) {
		Consumer<BigDecimal> commit = value -> parentSession.updatePrice(editedSlot, value);
		String               title  = "&8Price Editor — Slot " + editedSlot;
		openInternal(admin, item, originalPrice, commit, reopenCallback, title);
	}

	public void openGeneric(Player admin, ItemStack displayItem, BigDecimal originalPrice, String titleSuffix,
	                        Consumer<BigDecimal> commit, Runnable reopenCallback) {
		String title = "&8Price Editor — " + titleSuffix;
		openInternal(admin, displayItem, originalPrice, commit, reopenCallback, title);
	}

	public void reopenAfterAnvil(Player admin) {
		PriceEditorSession session = active.get(admin);
		if (session == null) return;

		session.expectingSubview = false;
		renderAll(session);
		session.handler.open(admin);
	}

	public void handleClose(Player admin, Inventory inventory) {
		PriceEditorSession session = active.get(admin);
		if (session == null) return;
		if (inventory != session.handler.getInventory()) return;
		if (session.expectingSubview) return;

		active.remove(admin);

		if (session.reopenCallback != null) {
			Bukkit.getScheduler().runTask(plugin, session.reopenCallback);
		}
	}

	private void openInternal(Player admin, ItemStack item, BigDecimal originalPrice, Consumer<BigDecimal> commit,
	                          Runnable reopenCallback, String title) {
		PriceEditorSession session = new PriceEditorSession(commit, item.clone(), originalPrice, reopenCallback);
		session.handler = new InventoryHandler(plugin, title, INVENTORY_SIZE, admin);

		fillGlass(session);
		renderAll(session);

		active.put(admin, session);
		session.handler.open(admin);
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void fillGlass(PriceEditorSession session) {
		ItemStack pane = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem();
		if (pane == null) pane = new ItemStack(Material.STONE);
		ItemBuilder filler = new ItemBuilder(pane).setDisplayName(" ");

		for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
			session.handler.setItem(slot, filler, false, (p, inv, b) -> { });
		}
	}

	private void renderAll(PriceEditorSession session) {
		renderInfo(session);
		renderItemPreview(session);
		renderAdjustmentButtons(session);
		renderCustomPriceButton(session);
		renderModeRow(session);
		renderSaveCancel(session);
	}

	private void renderInfo(PriceEditorSession session) {
		ItemBuilder info = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		info.setDisplayName("&eSetting price")
		    .setLore("&7Original price: &6$" + NumberUtil.valueFormat(session.originalPrice),
		             "&7Staged price: &6$" + NumberUtil.valueFormat(session.stagedPrice), " ",
		             "&8Use the green/red buttons to adjust,", "&8or click the yellow block for a custom value.");
		session.handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });
	}

	private void renderItemPreview(PriceEditorSession session) {
		ItemBuilder preview = new ItemBuilder(session.item.clone());
		preview.setLore("&7Staged price: &6$" + NumberUtil.valueFormat(session.stagedPrice),
		                "&7Current multiplier: &b" + session.mode);
		session.handler.setItem(SLOT_ITEM, preview, false, (p, inv, b) -> { });
	}

	private void renderAdjustmentButtons(PriceEditorSession session) {
		for (int i = 0; i < GREEN_SLOTS.length; i++) {
			int greenMagnitude = i + 1;
			int greenStep      = greenMagnitude * session.mode;

			ItemBuilder green = new ItemBuilder(material(XMaterial.LIME_CONCRETE, Material.GREEN_WOOL));
			green.setDisplayName("&a+ $" + NumberUtil.valueFormat(greenStep))
			     .setLore("&7Adds &a" + greenMagnitude + " &7× &b" + session.mode);
			final int greenDelta = greenStep;
			session.handler.setItem(GREEN_SLOTS[i], green, false, (p, inv, b) -> {
				SOUND_ADD.playSound(p);
				adjustPrice(session, BigDecimal.valueOf(greenDelta));
			});

			// Mirror the green row around the preview: largest step nearest the item, smallest at the edge.
			int redMagnitude = RED_SLOTS.length - i;
			int redStep      = redMagnitude * session.mode;

			ItemBuilder red = new ItemBuilder(material(XMaterial.RED_CONCRETE, Material.RED_WOOL));
			red.setDisplayName("&c- $" + NumberUtil.valueFormat(redStep))
			   .setLore("&7Subtracts &c" + redMagnitude + " &7× &b" + session.mode);
			final int redDelta = redStep;
			session.handler.setItem(RED_SLOTS[i], red, false, (p, inv, b) -> {
				SOUND_SUB.playSound(p);
				adjustPrice(session, BigDecimal.valueOf(-redDelta));
			});
		}
	}

	private void renderCustomPriceButton(PriceEditorSession session) {
		ItemBuilder button = new ItemBuilder(material(XMaterial.YELLOW_CONCRETE, Material.GOLD_BLOCK));
		button.setDisplayName("&eCustom price: &6$" + NumberUtil.valueFormat(session.stagedPrice))
		      .setLore("&7Click to type an exact price.");
		session.handler.setItem(SLOT_PRICE_ANVIL, button, false, (p, inv, b) -> {
			SOUND_ANVIL_PRICE.playSound(p);
			openPriceAnvil(p, session);
		});
	}

	private void renderModeRow(PriceEditorSession session) {
		ItemBuilder down = new ItemBuilder(material(XMaterial.BLUE_CONCRETE, Material.LAPIS_BLOCK));
		down.setDisplayName("&9◄ Previous multiplier").setLore("&7Wraps through 1 to " + MAX_MODE_CYCLE + ".");
		session.handler.setItem(SLOT_MODE_DOWN, down, false, (p, inv, b) -> {
			SOUND_MODE_DOWN.playSound(p);
			cycleMode(session, false);
		});

		ItemBuilder middle = new ItemBuilder(material(XMaterial.MAGENTA_CONCRETE, Material.PURPUR_BLOCK));
		middle.setDisplayName("&dCurrent multiplier: &b" + session.mode)
		      .setLore("&7Click to type a custom multiplier.",
		               "&8Max: " + NumberUtil.valueFormat(settings.getMaxModeMultiplier()));
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

	private void renderSaveCancel(PriceEditorSession session) {
		ItemBuilder save = new ItemBuilder(material(XMaterial.LIME_WOOL, Material.GREEN_WOOL));
		save.setDisplayName("&aSAVE price")
		    .setLore("&7Write &6$" + NumberUtil.valueFormat(session.stagedPrice) + " &7to the shop.");
		session.handler.setItem(SLOT_SAVE, save, false, (p, inv, b) -> {
			SOUND_SAVE.playSound(p);
			save(p, session);
		});

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cCANCEL").setLore("&7Discard changes and return.");
		session.handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> {
			SOUND_CANCEL.playSound(p);
			cancel(p);
		});
	}

	// ── Actions ──────────────────────────────────────────────────────────

	private void adjustPrice(PriceEditorSession session, BigDecimal delta) {
		BigDecimal next = session.stagedPrice.add(delta);
		session.stagedPrice = next.signum() < 0 ? BigDecimal.ZERO : next;
		renderInfo(session);
		renderItemPreview(session);
		renderCustomPriceButton(session);
		renderSaveCancel(session);
	}

	private void cycleMode(PriceEditorSession session, boolean forward) {
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

	private void save(Player admin, PriceEditorSession session) {
		session.onSave.accept(session.stagedPrice);
		session.committed = true;
		admin.closeInventory();
	}

	private void cancel(Player admin) {
		admin.closeInventory();
	}

	private void openPriceAnvil(Player admin, PriceEditorSession session) {
		session.expectingSubview = true;

		AnvilGUI.Builder builder = new AnvilGUI.Builder();
		builder.plugin(plugin)
		       .title("Set Price")
		       .itemLeft(material(XMaterial.PAPER, Material.PAPER))
		       .text(session.stagedPrice.toPlainString())
		       .onClick((slot, state) -> {
				   if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

				   String raw = state.getText() == null ? "" : state.getText().trim();
				   try {
					   BigDecimal value = new BigDecimal(raw);
					   if (value.signum() < 0) {
						   admin.sendMessage(ChatUtil.color("&cPrice cannot be negative."));
						   return Collections.emptyList();
					   }
					   session.stagedPrice = value;
					   return List.of(AnvilGUI.ResponseAction.close());
				   } catch (NumberFormatException e) {
					   admin.sendMessage(ChatUtil.color("&cInvalid number: " + raw));
					   return Collections.emptyList();
				   }
			   })
		       .onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> reopenAfterAnvil(admin)))
		       .open(admin);
	}

	private void openModeAnvil(Player admin, PriceEditorSession session) {
		session.expectingSubview = true;

		int cap = Math.max(1, settings.getMaxModeMultiplier());

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
						   admin.sendMessage(ChatUtil.color("&cMultiplier must be at least 1."));
						   return Collections.emptyList();
					   }
					   session.mode = Math.min(value, cap);
					   if (value > cap) {
						   admin.sendMessage(ChatUtil.color(
								   "&eMultiplier capped at &f" + cap + " &e(settings Max_Mode_Multiplier)."));
					   }
					   return List.of(AnvilGUI.ResponseAction.close());
				   } catch (NumberFormatException e) {
					   admin.sendMessage(ChatUtil.color("&cInvalid integer: " + raw));
					   return Collections.emptyList();
				   }
			   })
		       .onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> reopenAfterAnvil(admin)))
		       .open(admin);
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}


	// ── Session ──────────────────────────────────────────────────────────

	public static final class PriceEditorSession {
		final Consumer<BigDecimal> onSave;
		final ItemStack            item;
		final BigDecimal           originalPrice;
		final Runnable             reopenCallback;

		InventoryHandler handler;
		BigDecimal       stagedPrice;
		int              mode             = 1;
		boolean          expectingSubview = false;
		boolean          committed        = false;

		PriceEditorSession(Consumer<BigDecimal> onSave, ItemStack item, BigDecimal originalPrice,
		                   Runnable reopenCallback) {
			this.onSave         = onSave;
			this.item           = item;
			this.originalPrice  = originalPrice;
			this.stagedPrice    = originalPrice;
			this.reopenCallback = reopenCallback;
		}
	}

}
