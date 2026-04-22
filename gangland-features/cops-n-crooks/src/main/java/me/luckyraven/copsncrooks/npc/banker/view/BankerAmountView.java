package me.luckyraven.copsncrooks.npc.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract.BankerSnapshot;
import me.luckyraven.copsncrooks.npc.banker.message.BankerMessageContract;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.economy.bank.Currency;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.flow.MultiPanelInventory;
import me.luckyraven.inventory.flow.Panel;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Amount-picker panel shared between deposit and withdraw flows. Stages a {@link BigDecimal} on the flow session and
 * clamps against the remaining daily quota + source balance (cash for deposit, bank for withdraw). The anvil "custom
 * amount" detour uses {@link MultiPanelInventory#suspend()} before opening the anvil and
 * {@link MultiPanelInventory#resume()} + {@link MultiPanelInventory#switchTo(String)} from the anvil's onClose callback
 * — the staged amount (stored on {@link BankerFlowSession}) survives the detour.
 */
@RequiredArgsConstructor
public final class BankerAmountView implements Panel<BankerFlowSession> {

	private static final int SIZE            = 54;
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

	private static final SoundConfiguration SOUND_ADD     = vanilla("UI_BUTTON_CLICK", 1.5f);
	private static final SoundConfiguration SOUND_SUB     = vanilla("UI_BUTTON_CLICK", 0.8f);
	private static final SoundConfiguration SOUND_MODE_UP = vanilla("BLOCK_NOTE_BLOCK_HAT", 1.5f);
	private static final SoundConfiguration SOUND_MODE_DN = vanilla("BLOCK_NOTE_BLOCK_HAT", 0.8f);
	private static final SoundConfiguration SOUND_CONFIRM = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundConfiguration SOUND_CANCEL  = vanilla("ENTITY_VILLAGER_NO", 1.0f);
	private static final SoundConfiguration SOUND_DENY    = vanilla("BLOCK_NOTE_BLOCK_BASS", 1.0f);

	private final JavaPlugin            plugin;
	private final BankerEconomyContract economy;
	private final BankerMessageContract messages;

	private static SoundConfiguration vanilla(String name, float pitch) {
		return new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, name, 0.6f, pitch);
	}

	private static String format(BigDecimal value) {
		return NumberUtil.valueFormat(value);
	}

	@Override
	public int size(BankerFlowSession session) {
		return SIZE;
	}

	@Override
	public String title(BankerFlowSession session) {
		String label = session.amountMode == Mode.WITHDRAW ? "Withdraw" : "Deposit";
		return "&8&l[&b&l" + session.displayName() + "&8&l] &7" + label;
	}

	@Override
	public void render(MultiPanelInventory<BankerFlowSession> host, InventoryHandler handler, Player viewer,
	                   BankerFlowSession session) {
		BankerSnapshot snap = economy.snapshot(viewer);

		if (!snap.hasBank() || session.amountMode == null) {
			renderStub(host, handler, "&cNo bank account", "&8Open an account first.");
			return;
		}

		BigDecimal max = computeMax(snap, session.amountMode);
		if (max.signum() <= 0) {
			String line = session.amountMode == Mode.DEPOSIT
			              ? "&8Nothing you can deposit right now."
			              : "&8Nothing you can withdraw right now.";
			renderStub(host, handler, "&cUnavailable", line);
			return;
		}

		// Initialize staged amount on first render; re-clamp against refreshed max on subsequent renders (user may
		// have deposited elsewhere between panel entries).
		if (session.amountStaged == null) session.amountStaged = Currency.of(BigDecimal.ONE.min(max));
		if (session.amountStaged.signum() < 0) session.amountStaged = Currency.ZERO;
		if (session.amountStaged.compareTo(max) > 0) session.amountStaged = max;

		fillGlass(handler);
		renderInfo(handler, session, max);
		renderItemPreview(handler, session, max);
		renderAdjustButtons(host, handler, session, max);
		renderCustomAnvilButton(host, handler, session, max);
		renderModeRow(host, handler, session);
		renderConfirmCancel(host, handler, session);
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

	private void renderStub(MultiPanelInventory<BankerFlowSession> host, InventoryHandler handler, String name,
	                        String lore) {
		fillGlass(handler);
		ItemBuilder info = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER));
		info.setDisplayName(name).setLore(lore);
		handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });

		ItemBuilder back = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL)).setDisplayName("&cBACK");
		// Center the lone BACK button on the bottom row (slot 49) instead of reusing SLOT_CANCEL (slot 50), which is
		// offset one column right of center and leaves the stub looking lopsided.
		handler.setItem(SLOT_CANCEL - 1, back, false, (p, inv, b) -> {
			host.back();
			playSoundNextTick(p, SOUND_CANCEL);
		});
	}

	private void fillGlass(InventoryHandler handler) {
		ItemStack pane = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem();
		if (pane == null) pane = new ItemStack(Material.STONE);
		ItemBuilder filler = new ItemBuilder(pane).setDisplayName(" ");
		for (int slot = 0; slot < SIZE; slot++) handler.setItem(slot, filler, false, (p, inv, b) -> { });
	}

	private void renderInfo(InventoryHandler handler, BankerFlowSession session, BigDecimal max) {
		ItemBuilder info = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		String      verb = session.amountMode == Mode.DEPOSIT ? "deposit" : "withdraw";
		info.setDisplayName("&eChoose an amount to " + verb)
		    .setLore("&7Staged: &a$" + format(session.amountStaged),
		             "&7Max: &f$" + format(max),
		             "&7Step: &b$" + format(stepFor(session.amountStepIndex)), " ",
		             "&8Green adds, red subtracts.",
		             "&8Yellow block = type an exact amount.");
		handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });
	}

	private void renderItemPreview(InventoryHandler handler, BankerFlowSession session, BigDecimal max) {
		XMaterial   preferred = session.amountMode == Mode.DEPOSIT ? XMaterial.EMERALD_BLOCK : XMaterial.GOLD_BLOCK;
		ItemBuilder preview   = new ItemBuilder(material(preferred, Material.STONE));
		preview.setDisplayName("&a$" + format(session.amountStaged))
		       .setLore("&7Step: &b$" + format(stepFor(session.amountStepIndex)), "&7Max: &f$" + format(max));
		handler.setItem(SLOT_ITEM, preview, false, (p, inv, b) -> { });
	}

	private void renderAdjustButtons(MultiPanelInventory<BankerFlowSession> host, InventoryHandler handler,
	                                 BankerFlowSession session, BigDecimal max) {
		BigDecimal step = stepFor(session.amountStepIndex);

		for (int i = 0; i < GREEN_SLOTS.length; i++) {
			int        greenMag  = i + 1;
			BigDecimal greenStep = step.multiply(BigDecimal.valueOf(greenMag));

			ItemBuilder green = new ItemBuilder(material(XMaterial.LIME_CONCRETE, Material.GREEN_WOOL));
			green.setDisplayName("&a+ $" + format(greenStep))
			     .setLore("&7Adds &a" + greenMag + " &7× &b$" + format(step));
			final BigDecimal greenDelta = greenStep;
			handler.setItem(GREEN_SLOTS[i], green, false, (p, inv, b) -> {
				adjust(host, session, greenDelta, max);
				playSoundNextTick(p, SOUND_ADD);
			});

			// Mirror outward: biggest step nearest the item, smallest at the edge.
			int        redMag  = RED_SLOTS.length - i;
			BigDecimal redStep = step.multiply(BigDecimal.valueOf(redMag));

			ItemBuilder red = new ItemBuilder(material(XMaterial.RED_CONCRETE, Material.RED_WOOL));
			red.setDisplayName("&c- $" + format(redStep))
			   .setLore("&7Subtracts &c" + redMag + " &7× &b$" + format(step));
			final BigDecimal redDelta = redStep.negate();
			handler.setItem(RED_SLOTS[i], red, false, (p, inv, b) -> {
				adjust(host, session, redDelta, max);
				playSoundNextTick(p, SOUND_SUB);
			});
		}
	}

	private void renderCustomAnvilButton(MultiPanelInventory<BankerFlowSession> host, InventoryHandler handler,
	                                     BankerFlowSession session, BigDecimal max) {
		ItemBuilder button = new ItemBuilder(material(XMaterial.YELLOW_CONCRETE, Material.GOLD_BLOCK));
		button.setDisplayName("&eCustom amount: &f$" + format(session.amountStaged))
		      .setLore("&7Click to type an exact amount.", "&8Max: &f$" + format(max));
		handler.setItem(SLOT_QTY_ANVIL, button, false, (p, inv, b) -> openAmountAnvil(host, p, session, max));
	}

	private void renderModeRow(MultiPanelInventory<BankerFlowSession> host, InventoryHandler handler,
	                           BankerFlowSession session) {
		ItemBuilder down = new ItemBuilder(material(XMaterial.BLUE_CONCRETE, Material.LAPIS_BLOCK));
		down.setDisplayName("&9◄ Smaller step").setLore("&7Step: &b$" + format(stepFor(session.amountStepIndex)));
		handler.setItem(SLOT_MODE_DOWN, down, false, (p, inv, b) -> {
			cycleMode(host, session, false);
			playSoundNextTick(p, SOUND_MODE_DN);
		});

		ItemBuilder label = new ItemBuilder(material(XMaterial.MAGENTA_CONCRETE, Material.PURPUR_BLOCK));
		label.setDisplayName("&dStep: &b$" + format(stepFor(session.amountStepIndex)))
		     .setLore("&7Use the arrows to change step size.");
		handler.setItem(SLOT_MODE_LABEL, label, false, (p, inv, b) -> { });

		ItemBuilder up = new ItemBuilder(material(XMaterial.BLUE_CONCRETE, Material.LAPIS_BLOCK));
		up.setDisplayName("&9Larger step ►").setLore("&7Step: &b$" + format(stepFor(session.amountStepIndex)));
		handler.setItem(SLOT_MODE_UP, up, false, (p, inv, b) -> {
			cycleMode(host, session, true);
			playSoundNextTick(p, SOUND_MODE_UP);
		});
	}

	private void renderConfirmCancel(MultiPanelInventory<BankerFlowSession> host, InventoryHandler handler,
	                                 BankerFlowSession session) {
		ItemBuilder confirm = new ItemBuilder(material(XMaterial.LIME_WOOL, Material.GREEN_WOOL));
		String      verb    = session.amountMode == Mode.DEPOSIT ? "Deposit" : "Withdraw";
		confirm.setDisplayName("&a&lCONFIRM — &f" + verb + " $" + format(session.amountStaged))
		       .setLore("&7Click to " + verb.toLowerCase() + " &6$" + format(session.amountStaged));
		handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> confirm(host, p, session));

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cCANCEL").setLore("&7Discard and go back.");
		handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> {
			host.back();
			playSoundNextTick(p, SOUND_CANCEL);
		});
	}

	private BigDecimal stepFor(int modeIndex) {
		int clamped = Math.clamp(modeIndex, 0, STEP_LADDER.length - 1);
		return STEP_LADDER[clamped];
	}

	// ── Actions ──────────────────────────────────────────────────────────

	private void adjust(MultiPanelInventory<BankerFlowSession> host, BankerFlowSession session, BigDecimal delta,
	                    BigDecimal max) {
		BigDecimal next = session.amountStaged.add(delta);
		if (next.signum() < 0) next = Currency.ZERO;
		if (next.compareTo(max) > 0) next = max;
		session.amountStaged = Currency.of(next);
		host.rerender();
	}

	private void cycleMode(MultiPanelInventory<BankerFlowSession> host, BankerFlowSession session, boolean forward) {
		int cap     = Math.min(STEP_LADDER.length, MAX_MODE_CYCLE);
		int current = session.amountStepIndex;
		int next    = forward ? (current + 1) % cap : (current - 1 + cap) % cap;
		session.amountStepIndex = next;
		host.rerender();
	}

	private void confirm(MultiPanelInventory<BankerFlowSession> host, Player viewer, BankerFlowSession session) {
		BigDecimal amount = session.amountStaged;
		if (amount == null || amount.signum() <= 0) {
			playSoundNextTick(viewer, SOUND_DENY);
			return;
		}

		BankerEconomyContract.Result result = session.amountMode == Mode.DEPOSIT
		                                      ? economy.tryDeposit(viewer, amount)
		                                      : economy.tryWithdraw(viewer, amount);

		SoundConfiguration sound = null;
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
			host.back();
		} else {
			host.rerender();
		}
		if (sound != null) playSoundNextTick(viewer, sound);
	}

	private void openAmountAnvil(MultiPanelInventory<BankerFlowSession> host, Player viewer, BankerFlowSession session,
	                             BigDecimal max) {
		host.suspend();

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
				   host.resume();
				   host.switchTo(BankerFlowSession.PANEL_AMOUNT);
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
	private void playSoundNextTick(Player player, SoundConfiguration sound) {
		Bukkit.getScheduler().runTask(plugin, () -> sound.playSound(player));
	}

	public enum Mode {
		DEPOSIT,
		WITHDRAW
	}

}
