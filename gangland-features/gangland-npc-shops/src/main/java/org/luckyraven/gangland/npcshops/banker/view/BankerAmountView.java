package org.luckyraven.gangland.npcshops.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.npcshops.banker.economy.BankerEconomyContract;
import org.luckyraven.gangland.npcshops.banker.economy.BankerEconomyContract.BankerSnapshot;
import org.luckyraven.gangland.npcshops.banker.message.BankerMessageContract;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.keystone.economy.Currency;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Amount-picker panel shared between deposit and withdraw flows. Stages a {@link BigDecimal} on the flow session and
 * clamps against the remaining daily quota + source balance (cash for deposit, bank for withdraw). The anvil "custom
 * amount" detour uses {@link MenuFlow#suspend()} before opening the anvil and {@link MenuFlow#resume()} +
 * {@link MenuFlow#switchTo(String)} from the anvil's onClose callback — the staged amount (stored on
 * {@link BankerFlowSession}) survives the detour.
 */
@RequiredArgsConstructor
public final class BankerAmountView implements Panel<BankerFlowSession> {

	private static final int ROWS            = 6;
	private static final int SLOT_INFO       = 4;
	private static final int SLOT_ITEM       = 22;
	private static final int SLOT_QTY_ANVIL  = 31;
	private static final int SLOT_MODE_DOWN  = 38;
	private static final int SLOT_MODE_LABEL = 40;
	private static final int SLOT_MODE_UP    = 42;
	private static final int SLOT_CONFIRM    = 48;
	private static final int SLOT_CANCEL     = 50;

	private static final int[]        GREEN_SLOTS    = {18, 19, 20, 21};
	private static final int[]        RED_SLOTS      = {23, 24, 25, 26};
	private static final int          MAX_MODE_CYCLE = 8;
	private static final BigDecimal[] STEP_LADDER    = {
			BigDecimal.valueOf(1),
			BigDecimal.valueOf(10),
			BigDecimal.valueOf(100),
			BigDecimal.valueOf(1_000),
			BigDecimal.valueOf(10_000),
			BigDecimal.valueOf(100_000),
			BigDecimal.valueOf(1_000_000),
			BigDecimal.valueOf(10_000_000)
	};

	private static final SoundEffect SOUND_ADD     = vanilla("UI_BUTTON_CLICK", 1.5f);
	private static final SoundEffect SOUND_SUB     = vanilla("UI_BUTTON_CLICK", 0.8f);
	private static final SoundEffect SOUND_MODE_UP = vanilla("BLOCK_NOTE_BLOCK_HAT", 1.5f);
	private static final SoundEffect SOUND_MODE_DN = vanilla("BLOCK_NOTE_BLOCK_HAT", 0.8f);
	private static final SoundEffect SOUND_CONFIRM = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundEffect SOUND_CANCEL  = vanilla("ENTITY_VILLAGER_NO", 1.0f);
	private static final SoundEffect SOUND_DENY    = vanilla("BLOCK_NOTE_BLOCK_BASS", 1.0f);

	private final JavaPlugin            plugin;
	private final BankerEconomyContract economy;
	private final BankerMessageContract messages;

	private static SoundEffect vanilla(String name, float pitch) {
		return new SoundEffect(SoundEffect.SoundType.VANILLA, name, 0.6f, pitch);
	}

	private static String format(BigDecimal value) {
		return NumberUtil.valueFormat(value);
	}

	@Override
	public int rows(BankerFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(BankerFlowSession session) {
		String label = session.amountMode == Mode.WITHDRAW ? "Withdraw" : "Deposit";
		return "&8&l[&b&l" + session.displayName() + "&8&l] &7" + label;
	}

	@Override
	public void render(MenuFlow<BankerFlowSession> flow, ChestMenuBuilder builder, BankerFlowSession session) {
		BankerSnapshot snap = economy.snapshot(flow.viewer());

		if (!snap.hasBank() || session.amountMode == null) {
			renderStub(flow, builder, "&cNo bank account", "&8Open an account first.");
			return;
		}

		BigDecimal max = computeMax(snap, session.amountMode);
		if (max.signum() <= 0) {
			String line = session.amountMode == Mode.DEPOSIT
			              ? "&8Nothing you can deposit right now."
			              : "&8Nothing you can withdraw right now.";
			renderStub(flow, builder, "&cUnavailable", line);
			return;
		}

		// Initialize staged amount on first render; re-clamp against refreshed max on subsequent renders (user may
		// have deposited elsewhere between panel entries).
		if (session.amountStaged == null) session.amountStaged = Currency.of(BigDecimal.ONE.min(max));
		if (session.amountStaged.signum() < 0) session.amountStaged = Currency.ZERO;
		if (session.amountStaged.compareTo(max) > 0) session.amountStaged = max;

		fillGlass(builder);
		renderInfo(builder, session, max);
		renderItemPreview(builder, session, max);
		renderAdjustButtons(flow, builder, session, max);
		renderCustomAnvilButton(flow, builder, session, max);
		renderModeRow(flow, builder, session);
		renderConfirmCancel(flow, builder, session);
	}

	private BigDecimal computeMax(BankerSnapshot snap, Mode mode) {
		if (mode == Mode.DEPOSIT) {
			BigDecimal capHeadroom = snap.currentTier() == null
			                         ? new BigDecimal(Long.MAX_VALUE)
			                         : snap.currentTier().maxBalance().subtract(snap.bankBalance()).max(Currency.ZERO);
			BigDecimal maxD = snap.cashBalance().min(snap.remainingDailyDeposit()).min(capHeadroom);
			return Currency.of(maxD.max(Currency.ZERO));
		}
		return Currency.of(snap.bankBalance().max(Currency.ZERO));
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void renderStub(MenuFlow<BankerFlowSession> flow, ChestMenuBuilder builder, String name, String lore) {
		fillGlass(builder);
		ItemBuilder info = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER));
		info.setDisplayName(name).setLore(lore);
		builder.slot(SLOT_INFO, ItemComponent.of(info));

		ItemBuilder back = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL)).setDisplayName("&cBACK");
		// Center the lone BACK button on the bottom row (slot 49) instead of reusing SLOT_CANCEL (slot 50), which is
		// offset one column right of center and leaves the stub looking lopsided.
		builder.slot(SLOT_CANCEL - 1, ItemComponent.of(back).onAnyClick(ctx -> {
			flow.back();
			playSoundNextTick(ctx.player(), SOUND_CANCEL);
		}));
	}

	private void fillGlass(ChestMenuBuilder builder) {
		ItemStack pane = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem();
		if (pane == null) pane = new ItemStack(Material.STONE);
		builder.fill(FillComponent.of(pane.getType()).name(" "));
	}

	private void renderInfo(ChestMenuBuilder builder, BankerFlowSession session, BigDecimal max) {
		ItemBuilder info = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		String      verb = session.amountMode == Mode.DEPOSIT ? "deposit" : "withdraw";
		info.setDisplayName("&eChoose an amount to " + verb)
		    .setLore("&7Staged: &a$" + format(session.amountStaged),
		             "&7Max: &f$" + format(max),
		             "&7Step: &b$" + format(stepFor(session.amountStepIndex)), " ",
		             "&8Green adds, red subtracts.",
		             "&8Yellow block = type an exact amount.");
		builder.slot(SLOT_INFO, ItemComponent.of(info));
	}

	private void renderItemPreview(ChestMenuBuilder builder, BankerFlowSession session, BigDecimal max) {
		XMaterial   preferred = session.amountMode == Mode.DEPOSIT ? XMaterial.EMERALD_BLOCK : XMaterial.GOLD_BLOCK;
		ItemBuilder preview   = new ItemBuilder(material(preferred, Material.STONE));
		preview.setDisplayName("&a$" + format(session.amountStaged))
		       .setLore("&7Step: &b$" + format(stepFor(session.amountStepIndex)), "&7Max: &f$" + format(max));
		builder.slot(SLOT_ITEM, ItemComponent.of(preview));
	}

	private void renderAdjustButtons(MenuFlow<BankerFlowSession> flow, ChestMenuBuilder builder,
	                                 BankerFlowSession session, BigDecimal max) {
		BigDecimal step = stepFor(session.amountStepIndex);

		for (int i = 0; i < GREEN_SLOTS.length; i++) {
			int        greenMag  = i + 1;
			BigDecimal greenStep = step.multiply(BigDecimal.valueOf(greenMag));

			ItemBuilder green = new ItemBuilder(material(XMaterial.LIME_CONCRETE, Material.GREEN_WOOL));
			green.setDisplayName("&a+ $" + format(greenStep))
			     .setLore("&7Adds &a" + greenMag + " &7× &b$" + format(step));
			final BigDecimal greenDelta = greenStep;
			builder.slot(GREEN_SLOTS[i], ItemComponent.of(green).onAnyClick(ctx -> {
				adjust(flow, session, greenDelta, max);
				playSoundNextTick(ctx.player(), SOUND_ADD);
			}));

			// Mirror outward: biggest step nearest the item, smallest at the edge.
			int        redMag  = RED_SLOTS.length - i;
			BigDecimal redStep = step.multiply(BigDecimal.valueOf(redMag));

			ItemBuilder red = new ItemBuilder(material(XMaterial.RED_CONCRETE, Material.RED_WOOL));
			red.setDisplayName("&c- $" + format(redStep))
			   .setLore("&7Subtracts &c" + redMag + " &7× &b$" + format(step));
			final BigDecimal redDelta = redStep.negate();
			builder.slot(RED_SLOTS[i], ItemComponent.of(red).onAnyClick(ctx -> {
				adjust(flow, session, redDelta, max);
				playSoundNextTick(ctx.player(), SOUND_SUB);
			}));
		}
	}

	private void renderCustomAnvilButton(MenuFlow<BankerFlowSession> flow, ChestMenuBuilder builder,
	                                     BankerFlowSession session, BigDecimal max) {
		ItemBuilder button = new ItemBuilder(material(XMaterial.YELLOW_CONCRETE, Material.GOLD_BLOCK));
		button.setDisplayName("&eCustom amount: &f$" + format(session.amountStaged))
		      .setLore("&7Click to type an exact amount.", "&8Max: &f$" + format(max));
		builder.slot(SLOT_QTY_ANVIL,
		            ItemComponent.of(button).onAnyClick(ctx -> openAmountAnvil(flow, ctx.player(), session, max)));
	}

	private void renderModeRow(MenuFlow<BankerFlowSession> flow, ChestMenuBuilder builder, BankerFlowSession session) {
		ItemBuilder down = new ItemBuilder(material(XMaterial.BLUE_CONCRETE, Material.LAPIS_BLOCK));
		down.setDisplayName("&9◄ Smaller step").setLore("&7Step: &b$" + format(stepFor(session.amountStepIndex)));
		builder.slot(SLOT_MODE_DOWN, ItemComponent.of(down).onAnyClick(ctx -> {
			cycleMode(flow, session, false);
			playSoundNextTick(ctx.player(), SOUND_MODE_DN);
		}));

		ItemBuilder label = new ItemBuilder(material(XMaterial.MAGENTA_CONCRETE, Material.PURPUR_BLOCK));
		label.setDisplayName("&dStep: &b$" + format(stepFor(session.amountStepIndex)))
		     .setLore("&7Use the arrows to change step size.");
		builder.slot(SLOT_MODE_LABEL, ItemComponent.of(label));

		ItemBuilder up = new ItemBuilder(material(XMaterial.BLUE_CONCRETE, Material.LAPIS_BLOCK));
		up.setDisplayName("&9Larger step ►").setLore("&7Step: &b$" + format(stepFor(session.amountStepIndex)));
		builder.slot(SLOT_MODE_UP, ItemComponent.of(up).onAnyClick(ctx -> {
			cycleMode(flow, session, true);
			playSoundNextTick(ctx.player(), SOUND_MODE_UP);
		}));
	}

	private void renderConfirmCancel(MenuFlow<BankerFlowSession> flow, ChestMenuBuilder builder,
	                                 BankerFlowSession session) {
		ItemBuilder confirm = new ItemBuilder(material(XMaterial.LIME_WOOL, Material.GREEN_WOOL));
		String      verb    = session.amountMode == Mode.DEPOSIT ? "Deposit" : "Withdraw";
		confirm.setDisplayName("&a&lCONFIRM — &f" + verb + " $" + format(session.amountStaged))
		       .setLore("&7Click to " + verb.toLowerCase() + " &6$" + format(session.amountStaged));
		builder.slot(SLOT_CONFIRM, ItemComponent.of(confirm).onAnyClick(ctx -> confirm(flow, ctx.player(), session)));

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cCANCEL").setLore("&7Discard and go back.");
		builder.slot(SLOT_CANCEL, ItemComponent.of(cancel).onAnyClick(ctx -> {
			flow.back();
			playSoundNextTick(ctx.player(), SOUND_CANCEL);
		}));
	}

	private BigDecimal stepFor(int modeIndex) {
		int clamped = Math.max(0, Math.min(modeIndex, STEP_LADDER.length - 1));
		return STEP_LADDER[clamped];
	}

	// ── Actions ──────────────────────────────────────────────────────────

	private void adjust(MenuFlow<BankerFlowSession> flow, BankerFlowSession session, BigDecimal delta, BigDecimal max) {
		BigDecimal next = session.amountStaged.add(delta);
		if (next.signum() < 0) next = Currency.ZERO;
		if (next.compareTo(max) > 0) next = max;
		session.amountStaged = Currency.of(next);
		flow.rerender();
	}

	private void cycleMode(MenuFlow<BankerFlowSession> flow, BankerFlowSession session, boolean forward) {
		int cap     = Math.min(STEP_LADDER.length, MAX_MODE_CYCLE);
		int current = session.amountStepIndex;
		int next    = forward ? (current + 1) % cap : (current - 1 + cap) % cap;
		session.amountStepIndex = next;
		flow.rerender();
	}

	private void confirm(MenuFlow<BankerFlowSession> flow, Player viewer, BankerFlowSession session) {
		BigDecimal amount = session.amountStaged;
		if (amount == null || amount.signum() <= 0) {
			playSoundNextTick(viewer, SOUND_DENY);
			return;
		}

		BankerEconomyContract.Result result = session.amountMode == Mode.DEPOSIT
		                                      ? economy.tryDeposit(viewer, amount)
		                                      : economy.tryWithdraw(viewer, amount);

		SoundEffect sound = null;
		String             msg;
		switch (result) {
			case SUCCESS -> {
				msg   = session.amountMode == Mode.DEPOSIT
				        ? messages.depositSuccess(amount)
				        : messages.withdrawSuccess(amount);
				sound = SOUND_CONFIRM;
			}
			case NO_ACCOUNT -> msg = messages.noAccount();
			case INSUFFICIENT_CASH -> {
				msg   = messages.insufficientCash(amount);
				sound = SOUND_DENY;
			}
			case INSUFFICIENT_BANK_FUNDS -> {
				msg   = messages.insufficientBankFunds(amount);
				sound = SOUND_DENY;
			}
			case DAILY_DEPOSIT_REACHED -> {
				BankerSnapshot snap = economy.snapshot(viewer);
				BigDecimal limit = snap.currentTier() == null
				                   ? Currency.ZERO : snap.currentTier().dailyDepositLimit();
				msg   = messages.dailyDepositReached(limit);
				sound = SOUND_DENY;
			}
			case CAP_EXCEEDED -> {
				BankerSnapshot snap = economy.snapshot(viewer);
				BigDecimal cap = snap.currentTier() == null
				                 ? Currency.ZERO : snap.currentTier().maxBalance();
				msg   = messages.capExceeded(cap);
				sound = SOUND_DENY;
			}
			default -> msg = null;
		}
		if (msg != null) viewer.sendMessage(msg);

		if (result == BankerEconomyContract.Result.SUCCESS) {
			// Reset staged + step on successful tx so the next entry starts fresh.
			session.amountStaged    = null;
			session.amountStepIndex = 0;
			flow.back();
		} else {
			flow.rerender();
		}
		if (sound != null) playSoundNextTick(viewer, sound);
	}

	private void openAmountAnvil(MenuFlow<BankerFlowSession> flow, Player viewer, BankerFlowSession session,
	                             BigDecimal max) {
		flow.suspend();

		AnvilGUI.Builder builder = new AnvilGUI.Builder();
		builder.plugin(plugin)
		       .title("Set Amount")
		       .itemLeft(material(XMaterial.PAPER, Material.PAPER))
		       .text(session.amountStaged == null ? "0" : session.amountStaged.toPlainString())
		       .onClick((slot, state) -> {
				   if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

				   String raw = state.getText() == null ? "" : state.getText().trim();
				   try {
					   BigDecimal value = Currency.parse(raw);
					   if (value.signum() < 0) {
						   viewer.sendMessage(ChatUtil.color("&cAmount must be non-negative."));
						   return Collections.emptyList();
					   }
					   BigDecimal clamped = value.min(max);
					   session.amountStaged = Currency.of(clamped);
					   if (value.compareTo(max) > 0) {
						   viewer.sendMessage(ChatUtil.color(
								   "&eAmount capped at &f$" + format(max) + "&e."));
					   }
					   return List.of(AnvilGUI.ResponseAction.close());
				   } catch (NumberFormatException e) {
					   viewer.sendMessage(ChatUtil.color("&cInvalid number: " + raw));
					   return Collections.emptyList();
				   }
			   })
		       .onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> {
				   flow.resume();
				   flow.switchTo(BankerFlowSession.PANEL_AMOUNT);
			   }))
		       .open(viewer);
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	/**
	 * Defers the sound by one tick so it plays after the panel swap / rerender has settled on the client. Playing the
	 * sound inline in the same tick as a flow transition makes the client render audio and inventory change together,
	 * which the viewer experiences as a flicker.
	 */
	private void playSoundNextTick(Player player, SoundEffect sound) {
		Bukkit.getScheduler().runTask(plugin, () -> sound.playSound(player));
	}

	public enum Mode {
		DEPOSIT,
		WITHDRAW
	}

}
