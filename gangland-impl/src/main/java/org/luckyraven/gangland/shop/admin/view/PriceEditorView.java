package org.luckyraven.gangland.shop.admin.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.gangland.shop.config.ShopUiSettings;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Price-edit panel in the shop-admin flow. Reads the edit context ({@code priceEditItem}, {@code priceEditOriginal},
 * {@code priceEditStaged}, {@code priceEditCommit}, …) from the shared {@link ShopAdminFlowSession}; on SAVE it calls
 * {@code priceEditCommit.accept(staged)} and returns to the caller via {@link MenuFlow#back()}. Custom price /
 * multiplier entry uses AnvilGUI with the standard {@code flow.suspend() → anvil → flow.resume() + switchTo} detour
 * (matches {@code QuantitySelectorView}'s pattern in npc-shops).
 *
 * <p>WS4 G1b: rebuilt onto {@code keystone-inventory} — no item-holding slot here (this panel only ever shows a
 * read-only preview of the item already selected on a prior panel; nothing is ever placed into it).
 */
@RequiredArgsConstructor
public final class PriceEditorView implements Panel<ShopAdminFlowSession> {

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
	private static final int   ROWS             = 6;

	private static final SoundEffect SOUND_ADD         = vanilla("UI_BUTTON_CLICK", 1.5f);
	private static final SoundEffect SOUND_SUB         = vanilla("UI_BUTTON_CLICK", 0.8f);
	private static final SoundEffect SOUND_MODE_UP     = vanilla("BLOCK_NOTE_BLOCK_HAT", 1.5f);
	private static final SoundEffect SOUND_MODE_DOWN   = vanilla("BLOCK_NOTE_BLOCK_HAT", 0.8f);
	private static final SoundEffect SOUND_ANVIL_PRICE = vanilla("BLOCK_ANVIL_USE", 1.2f);
	private static final SoundEffect SOUND_ANVIL_MODE  = vanilla("BLOCK_ANVIL_USE", 1.0f);
	private static final SoundEffect SOUND_SAVE        = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundEffect SOUND_CANCEL      = vanilla("ENTITY_VILLAGER_NO", 1.0f);

	private final JavaPlugin     plugin;
	private final ShopUiSettings settings;

	private static SoundEffect vanilla(String name, float pitch) {
		return new SoundEffect(SoundEffect.SoundType.VANILLA, name, 0.6f, pitch);
	}

	@Override
	public int rows(ShopAdminFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(ShopAdminFlowSession session) {
		String suffix = session.priceEditTitleSuffix != null ? session.priceEditTitleSuffix : "Price";
		return "&8Price Editor — " + suffix;
	}

	@Override
	public void render(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder, ShopAdminFlowSession session) {
		if (session.priceEditItem == null || session.priceEditCommit == null) {
			// Shouldn't happen in practice — caller always populates before switchTo. Fall back to a stub with back.
			renderStub(flow, builder);
			return;
		}
		if (session.priceEditStaged == null) {
			session.priceEditStaged = session.priceEditOriginal != null ? session.priceEditOriginal : BigDecimal.ZERO;
		}

		renderInfo(builder, session);
		renderItemPreview(builder, session);
		renderAdjustmentButtons(flow, builder, session);
		renderCustomPriceButton(flow, builder, session);
		renderModeRow(flow, builder, session);
		renderSaveCancel(flow, builder, session);

		builder.fill(FillComponent.of(fillMaterial()).name(" "));
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void renderStub(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder) {
		ItemBuilder info = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER));
		info.setDisplayName("&cNothing to edit").setLore("&8Missing edit context — returning.");
		builder.slot(SLOT_INFO, ItemComponent.of(info));
		ItemBuilder back = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL)).setDisplayName("&cBACK");
		builder.slot(SLOT_CANCEL - 1, ItemComponent.of(back).onAnyClick(ctx -> flow.back()));
		builder.fill(FillComponent.of(fillMaterial()).name(" "));
	}

	private Material fillMaterial() {
		ItemStack pane = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem();
		return pane != null ? pane.getType() : Material.STONE;
	}

	private void renderInfo(ChestMenuBuilder builder, ShopAdminFlowSession session) {
		ItemBuilder info = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		info.setDisplayName("&eSetting price")
		    .setLore("&7Original price: &6$" + NumberUtil.valueFormat(session.priceEditOriginal),
		             "&7Staged price: &6$" + NumberUtil.valueFormat(session.priceEditStaged), " ",
		             "&8Use the green/red buttons to adjust,", "&8or click the yellow block for a custom value.");
		builder.slot(SLOT_INFO, ItemComponent.of(info));
	}

	private void renderItemPreview(ChestMenuBuilder builder, ShopAdminFlowSession session) {
		ItemBuilder preview = new ItemBuilder(session.priceEditItem.clone());
		preview.setLore("&7Staged price: &6$" + NumberUtil.valueFormat(session.priceEditStaged),
		                "&7Current multiplier: &b" + session.priceEditMode);
		builder.slot(SLOT_ITEM, ItemComponent.of(preview));
	}

	private void renderAdjustmentButtons(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder,
	                                     ShopAdminFlowSession session) {
		for (int i = 0; i < GREEN_SLOTS.length; i++) {
			int greenMagnitude = i + 1;
			int greenStep      = greenMagnitude * session.priceEditMode;

			ItemBuilder green = new ItemBuilder(material(XMaterial.LIME_CONCRETE, Material.GREEN_WOOL));
			green.setDisplayName("&a+ $" + NumberUtil.valueFormat(greenStep))
			     .setLore("&7Adds &a" + greenMagnitude + " &7× &b" + session.priceEditMode);
			final int greenDelta = greenStep;
			builder.slot(GREEN_SLOTS[i], ItemComponent.of(green).onAnyClick(ctx -> {
				adjustPrice(flow, session, BigDecimal.valueOf(greenDelta));
				Bukkit.getScheduler().runTask(plugin, () -> SOUND_ADD.playSound(ctx.player()));
			}));

			int redMagnitude = RED_SLOTS.length - i;
			int redStep      = redMagnitude * session.priceEditMode;

			ItemBuilder red = new ItemBuilder(material(XMaterial.RED_CONCRETE, Material.RED_WOOL));
			red.setDisplayName("&c- $" + NumberUtil.valueFormat(redStep))
			   .setLore("&7Subtracts &c" + redMagnitude + " &7× &b" + session.priceEditMode);
			final int redDelta = redStep;
			builder.slot(RED_SLOTS[i], ItemComponent.of(red).onAnyClick(ctx -> {
				adjustPrice(flow, session, BigDecimal.valueOf(-redDelta));
				Bukkit.getScheduler().runTask(plugin, () -> SOUND_SUB.playSound(ctx.player()));
			}));
		}
	}

	private void renderCustomPriceButton(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder,
	                                     ShopAdminFlowSession session) {
		ItemBuilder button = new ItemBuilder(material(XMaterial.YELLOW_CONCRETE, Material.GOLD_BLOCK));
		button.setDisplayName("&eCustom price: &6$" + NumberUtil.valueFormat(session.priceEditStaged))
		      .setLore("&7Click to type an exact price.");
		builder.slot(SLOT_PRICE_ANVIL, ItemComponent.of(button).onAnyClick(ctx -> {
			openPriceAnvil(flow, ctx.player(), session);
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_ANVIL_PRICE.playSound(ctx.player()));
		}));
	}

	private void renderModeRow(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder,
	                           ShopAdminFlowSession session) {
		ItemBuilder down = new ItemBuilder(material(XMaterial.BLUE_CONCRETE, Material.LAPIS_BLOCK));
		down.setDisplayName("&9◄ Previous multiplier").setLore("&7Wraps through 1 to " + MAX_MODE_CYCLE + ".");
		builder.slot(SLOT_MODE_DOWN, ItemComponent.of(down).onAnyClick(ctx -> {
			cycleMode(flow, session, false);
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_MODE_DOWN.playSound(ctx.player()));
		}));

		ItemBuilder middle = new ItemBuilder(material(XMaterial.MAGENTA_CONCRETE, Material.PURPUR_BLOCK));
		middle.setDisplayName("&dCurrent multiplier: &b" + session.priceEditMode)
		      .setLore("&7Click to type a custom multiplier.",
		               "&8Max: " + NumberUtil.valueFormat(settings.getMaxModeMultiplier()));
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

	private void renderSaveCancel(MenuFlow<ShopAdminFlowSession> flow, ChestMenuBuilder builder,
	                              ShopAdminFlowSession session) {
		ItemBuilder save = new ItemBuilder(material(XMaterial.LIME_WOOL, Material.GREEN_WOOL));
		save.setDisplayName("&aSAVE price")
		    .setLore("&7Write &6$" + NumberUtil.valueFormat(session.priceEditStaged) + " &7to the shop.");
		builder.slot(SLOT_SAVE, ItemComponent.of(save).onAnyClick(ctx -> {
			if (session.priceEditCommit != null) session.priceEditCommit.accept(session.priceEditStaged);
			clearEditContext(session);
			flow.back();
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_SAVE.playSound(ctx.player()));
		}));

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cCANCEL").setLore("&7Discard changes and return.");
		builder.slot(SLOT_CANCEL, ItemComponent.of(cancel).onAnyClick(ctx -> {
			clearEditContext(session);
			flow.back();
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_CANCEL.playSound(ctx.player()));
		}));
	}

	// ── Actions ──────────────────────────────────────────────────────────

	private void adjustPrice(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session, BigDecimal delta) {
		BigDecimal next = session.priceEditStaged.add(delta);
		session.priceEditStaged = next.signum() < 0 ? BigDecimal.ZERO : next;
		flow.rerender();
	}

	private void cycleMode(MenuFlow<ShopAdminFlowSession> flow, ShopAdminFlowSession session, boolean forward) {
		int current = session.priceEditMode;
		int capped  = Math.min(current, MAX_MODE_CYCLE);
		int next;
		if (forward) next = (capped % MAX_MODE_CYCLE) + 1;
		else next = ((capped - 2 + MAX_MODE_CYCLE) % MAX_MODE_CYCLE) + 1;
		session.priceEditMode = next;
		flow.rerender();
	}

	private void openPriceAnvil(MenuFlow<ShopAdminFlowSession> flow, Player viewer, ShopAdminFlowSession session) {
		flow.suspend();
		new AnvilGUI.Builder()
				.plugin(plugin)
				.title("Set Price")
				.itemLeft(material(XMaterial.PAPER, Material.PAPER))
				.text(session.priceEditStaged.toPlainString())
				.onClick((slot, state) -> {
					if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
					String raw = state.getText() == null ? "" : state.getText().trim();
					try {
						BigDecimal value = new BigDecimal(raw);
						if (value.signum() < 0) {
							viewer.sendMessage(ChatUtil.color("&cPrice cannot be negative."));
							return Collections.emptyList();
						}
						session.priceEditStaged = value;
						return List.of(AnvilGUI.ResponseAction.close());
					} catch (NumberFormatException e) {
						viewer.sendMessage(ChatUtil.color("&cInvalid number: " + raw));
						return Collections.emptyList();
					}
				})
				.onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> {
					flow.resume();
					flow.switchTo(ShopAdminFlowSession.PANEL_PRICE_EDITOR);
				}))
				.open(viewer);
	}

	private void openModeAnvil(MenuFlow<ShopAdminFlowSession> flow, Player viewer, ShopAdminFlowSession session) {
		int cap = Math.max(1, settings.getMaxModeMultiplier());

		flow.suspend();
		new AnvilGUI.Builder()
				.plugin(plugin)
				.title("Set Multiplier")
				.itemLeft(material(XMaterial.PAPER, Material.PAPER))
				.text(String.valueOf(session.priceEditMode))
				.onClick((slot, state) -> {
					if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
					String raw = state.getText() == null ? "" : state.getText().trim();
					try {
						int value = Integer.parseInt(raw);
						if (value < 1) {
							viewer.sendMessage(ChatUtil.color("&cMultiplier must be at least 1."));
							return Collections.emptyList();
						}
						session.priceEditMode = Math.min(value, cap);
						if (value > cap) {
							viewer.sendMessage(ChatUtil.color(
									"&eMultiplier capped at &f" + cap + " &e(settings Shop.Max_Mode_Multiplier)."));
						}
						return List.of(AnvilGUI.ResponseAction.close());
					} catch (NumberFormatException e) {
						viewer.sendMessage(ChatUtil.color("&cInvalid integer: " + raw));
						return Collections.emptyList();
					}
				})
				.onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> {
					flow.resume();
					flow.switchTo(ShopAdminFlowSession.PANEL_PRICE_EDITOR);
				}))
				.open(viewer);
	}

	/**
	 * Clears per-entry edit context on exit so the next SAVE/CANCEL click cannot commit the stale context.
	 */
	private void clearEditContext(ShopAdminFlowSession session) {
		session.priceEditItem        = null;
		session.priceEditOriginal    = null;
		session.priceEditStaged      = null;
		session.priceEditMode        = 1;
		session.priceEditTitleSuffix = null;
		session.priceEditCommit      = null;
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

}
