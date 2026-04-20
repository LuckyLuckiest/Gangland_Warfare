package me.luckyraven.copsncrooks.npc.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.banker.BankerNpc;
import me.luckyraven.copsncrooks.npc.banker.config.BankerSettings;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract.BankerSnapshot;
import me.luckyraven.copsncrooks.npc.banker.message.BankerMessageContract;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.util.utilities.NumberUtil;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Amount picker shared between deposit and withdraw flows. Mirrors shop-api's QuantitySelectorView but stages a
 * {@code double} amount (currency) rather than a copy count, and clamps against the remaining daily quota + the
 * relevant source balance (cash for deposit, bank for withdraw).
 */
@RequiredArgsConstructor
public final class BankerAmountView {

	private static final int   SIZE            = 54;
	private static final int   SLOT_INFO       = 4;
	private static final int   SLOT_ITEM       = 22;
	private static final int   SLOT_QTY_ANVIL  = 31;
	private static final int   SLOT_MODE_DOWN  = 38;
	private static final int   SLOT_MODE_LABEL = 40;
	private static final int   SLOT_MODE_UP    = 42;
	private static final int   SLOT_CONFIRM    = 48;
	private static final int   SLOT_CANCEL     = 50;
	private static final int[] GREEN_SLOTS     = {18, 19, 20, 21};
	private static final int[] RED_SLOTS       = {23, 24, 25, 26};
	private static final int   MAX_MODE_CYCLE  = 8;
	private static final int[] STEP_LADDER     = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000};
	private static final SoundConfiguration SOUND_ADD     = vanilla("UI_BUTTON_CLICK", 1.5f);
	private static final SoundConfiguration SOUND_SUB     = vanilla("UI_BUTTON_CLICK", 0.8f);
	private static final SoundConfiguration SOUND_MODE_UP = vanilla("BLOCK_NOTE_BLOCK_HAT", 1.5f);
	private static final SoundConfiguration SOUND_MODE_DN = vanilla("BLOCK_NOTE_BLOCK_HAT", 0.8f);
	private static final SoundConfiguration SOUND_CONFIRM = vanilla("ENTITY_PLAYER_LEVELUP", 1.0f);
	private static final SoundConfiguration SOUND_CANCEL  = vanilla("ENTITY_VILLAGER_NO", 1.0f);
	private static final SoundConfiguration SOUND_DENY    = vanilla("BLOCK_NOTE_BLOCK_BASS", 1.0f);
	private final JavaPlugin            plugin;
	private final BankerSettings        settings;
	private final BankerEconomyContract economy;
	private final BankerMessageContract messages;
	private final Map<Player, Session> active = new WeakHashMap<>();
	private       BankerMenuView        menuView;

	private static SoundConfiguration vanilla(String name, float pitch) {
		return new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, name, 0.6f, pitch);
	}

	public void setMenuView(BankerMenuView menuView) {
		this.menuView = menuView;
	}

	public void open(Player viewer, BankerNpc banker, Mode mode) {
		BankerSnapshot snap = economy.snapshot(viewer);
		if (!snap.hasBank()) {
			viewer.sendMessage(messages.noAccount());
			SOUND_DENY.playSound(viewer);
			return;
		}

		double max = computeMax(snap, mode);
		if (max <= 0) {
			viewer.sendMessage(ChatUtil.color(mode == Mode.DEPOSIT
			                                  ? "&cNothing you can deposit right now."
			                                  : "&cNothing you can withdraw right now."));
			SOUND_DENY.playSound(viewer);
			if (menuView != null) Bukkit.getScheduler().runTask(plugin, () -> menuView.open(viewer, banker));
			return;
		}

		Session session = new Session(banker, mode, max, Math.min(1, (long) max));
		session.handler = new InventoryHandler(plugin, titleFor(mode, banker), SIZE, viewer);

		fillGlass(session);
		renderAll(session);

		active.put(viewer, session);
		session.handler.open(viewer);
	}

	public void reopenAfterAnvil(Player viewer) {
		Session session = active.get(viewer);
		if (session == null) return;
		session.expectingSubview = false;
		renderAll(session);
		session.handler.open(viewer);
	}

	public void handleClose(Player viewer, org.bukkit.inventory.Inventory inventory) {
		Session session = active.get(viewer);
		if (session == null) return;
		if (inventory != session.handler.getInventory()) return;
		if (session.expectingSubview) return;
		active.remove(viewer);
	}

	private double computeMax(BankerSnapshot snap, Mode mode) {
		if (mode == Mode.DEPOSIT) {
			double capHeadroom = snap.currentTier() == null
			                     ? Double.MAX_VALUE
			                     : Math.max(0, snap.currentTier().maxBalance() - snap.bankBalance());
			double maxD = Math.min(snap.cashBalance(), snap.remainingDailyDeposit());
			maxD = Math.min(maxD, capHeadroom);
			return Math.max(0, Math.floor(maxD));
		} else {
			double maxW = Math.min(snap.bankBalance(), snap.remainingDailyWithdraw());
			return Math.max(0, Math.floor(maxW));
		}
	}

	private String titleFor(Mode mode, BankerNpc banker) {
		String label = mode == Mode.DEPOSIT ? "Deposit" : "Withdraw";
		String name  = banker.getData().getDisplayName() != null ? banker.getData().getDisplayName() : "Banker";
		return "&8&l[&b&l" + name + "&8&l] &7" + label;
	}

	private void fillGlass(Session session) {
		ItemStack pane = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem();
		if (pane == null) pane = new ItemStack(Material.STONE);
		ItemBuilder filler = new ItemBuilder(pane).setDisplayName(" ");
		for (int slot = 0; slot < SIZE; slot++) {
			session.handler.setItem(slot, filler, false, (p, inv, b) -> { });
		}
	}

	// ── Rendering ────────────────────────────────────────────────────────

	private void renderAll(Session session) {
		renderInfo(session);
		renderItemPreview(session);
		renderAdjustButtons(session);
		renderCustomAnvilButton(session);
		renderModeRow(session);
		renderConfirmCancel(session);
	}

	private void renderInfo(Session session) {
		ItemBuilder info = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		String      verb = session.mode == Mode.DEPOSIT ? "deposit" : "withdraw";
		info.setDisplayName("&eChoose an amount to " + verb)
		    .setLore("&7Staged: &a$" + NumberUtil.valueFormat(session.amount),
		             "&7Max: &f$" + NumberUtil.valueFormat(session.maxAmount),
		             "&7Step: &b$" + stepFor(session.modeIndex),
		             " ",
		             "&8Green adds, red subtracts.",
		             "&8Yellow block = type an exact amount.");
		session.handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });
	}

	private void renderItemPreview(Session session) {
		XMaterial   preferred = session.mode == Mode.DEPOSIT ? XMaterial.EMERALD_BLOCK : XMaterial.GOLD_BLOCK;
		ItemBuilder preview   = new ItemBuilder(material(preferred, Material.STONE));
		preview.setDisplayName("&a$" + NumberUtil.valueFormat(session.amount))
		       .setLore("&7Step: &b$" + stepFor(session.modeIndex),
		                "&7Max: &f$" + NumberUtil.valueFormat(session.maxAmount));
		session.handler.setItem(SLOT_ITEM, preview, false, (p, inv, b) -> { });
	}

	private void renderAdjustButtons(Session session) {
		long step = stepFor(session.modeIndex);

		for (int i = 0; i < GREEN_SLOTS.length; i++) {
			int  greenMag  = i + 1;
			long greenStep = greenMag * step;

			ItemBuilder green = new ItemBuilder(material(XMaterial.LIME_CONCRETE, Material.GREEN_WOOL));
			green.setDisplayName("&a+ $" + NumberUtil.valueFormat(greenStep))
			     .setLore("&7Adds &a" + greenMag + " &7× &b$" + step);
			final long greenDelta = greenStep;
			session.handler.setItem(GREEN_SLOTS[i], green, false, (p, inv, b) -> {
				SOUND_ADD.playSound(p);
				adjust(session, +greenDelta);
			});

			// Mirror outward: biggest step nearest the item, smallest at the edge.
			int  redMag  = RED_SLOTS.length - i;
			long redStep = redMag * step;

			ItemBuilder red = new ItemBuilder(material(XMaterial.RED_CONCRETE, Material.RED_WOOL));
			red.setDisplayName("&c- $" + NumberUtil.valueFormat(redStep))
			   .setLore("&7Subtracts &c" + redMag + " &7× &b$" + step);
			final long redDelta = redStep;
			session.handler.setItem(RED_SLOTS[i], red, false, (p, inv, b) -> {
				SOUND_SUB.playSound(p);
				adjust(session, -redDelta);
			});
		}
	}

	private void renderCustomAnvilButton(Session session) {
		ItemBuilder button = new ItemBuilder(material(XMaterial.YELLOW_CONCRETE, Material.GOLD_BLOCK));
		button.setDisplayName("&eCustom amount: &f$" + NumberUtil.valueFormat(session.amount))
		      .setLore("&7Click to type an exact amount.",
		               "&8Max: &f$" + NumberUtil.valueFormat(session.maxAmount));
		session.handler.setItem(SLOT_QTY_ANVIL, button, false, (p, inv, b) -> openAmountAnvil(p, session));
	}

	private void renderModeRow(Session session) {
		ItemBuilder down = new ItemBuilder(material(XMaterial.BLUE_CONCRETE, Material.LAPIS_BLOCK));
		down.setDisplayName("&9◄ Smaller step").setLore("&7Step: &b$" + stepFor(session.modeIndex));
		session.handler.setItem(SLOT_MODE_DOWN, down, false, (p, inv, b) -> {
			SOUND_MODE_DN.playSound(p);
			cycleMode(session, false);
		});

		ItemBuilder label = new ItemBuilder(material(XMaterial.MAGENTA_CONCRETE, Material.PURPUR_BLOCK));
		label.setDisplayName("&dStep: &b$" + stepFor(session.modeIndex))
		     .setLore("&7Use the arrows to change step size.");
		session.handler.setItem(SLOT_MODE_LABEL, label, false, (p, inv, b) -> { });

		ItemBuilder up = new ItemBuilder(material(XMaterial.BLUE_CONCRETE, Material.LAPIS_BLOCK));
		up.setDisplayName("&9Larger step ►").setLore("&7Step: &b$" + stepFor(session.modeIndex));
		session.handler.setItem(SLOT_MODE_UP, up, false, (p, inv, b) -> {
			SOUND_MODE_UP.playSound(p);
			cycleMode(session, true);
		});
	}

	private void renderConfirmCancel(Session session) {
		ItemBuilder confirm = new ItemBuilder(material(XMaterial.LIME_WOOL, Material.GREEN_WOOL));
		String      verb    = session.mode == Mode.DEPOSIT ? "Deposit" : "Withdraw";
		confirm.setDisplayName("&a&lCONFIRM — &f" + verb + " $" + NumberUtil.valueFormat(session.amount))
		       .setLore("&7Click to " + verb.toLowerCase() + " &6$" + NumberUtil.valueFormat(session.amount));
		session.handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> {
			SOUND_CONFIRM.playSound(p);
			confirm(p, session);
		});

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cCANCEL").setLore("&7Discard and go back.");
		session.handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> {
			SOUND_CANCEL.playSound(p);
			cancel(p, session);
		});
	}

	private long stepFor(int modeIndex) {
		int clamped = Math.clamp(modeIndex, 0, STEP_LADDER.length - 1);
		return STEP_LADDER[clamped];
	}

	private void adjust(Session session, long delta) {
		long next = Math.round(session.amount) + delta;
		session.amount = Math.clamp(next, 0, (long) session.maxAmount);
		renderInfo(session);
		renderItemPreview(session);
		renderCustomAnvilButton(session);
		renderConfirmCancel(session);
	}

	// ── Actions ──────────────────────────────────────────────────────────

	private void cycleMode(Session session, boolean forward) {
		int current = session.modeIndex;
		int next;
		if (forward) next = (current + 1) % Math.min(STEP_LADDER.length, MAX_MODE_CYCLE);
		else next = (current - 1 + Math.min(STEP_LADDER.length, MAX_MODE_CYCLE))
		            % Math.min(STEP_LADDER.length, MAX_MODE_CYCLE);
		session.modeIndex = next;
		renderAdjustButtons(session);
		renderModeRow(session);
		renderInfo(session);
		renderItemPreview(session);
	}

	private void confirm(Player viewer, Session session) {
		double amount = session.amount;
		if (amount <= 0) {
			SOUND_DENY.playSound(viewer);
			return;
		}

		BankerEconomyContract.Result result = session.mode == Mode.DEPOSIT
		                                      ? economy.tryDeposit(viewer, amount)
		                                      : economy.tryWithdraw(viewer, amount);

		String msg;
		switch (result) {
			case SUCCESS -> msg = session.mode == Mode.DEPOSIT
			                      ? messages.depositSuccess(amount)
			                      : messages.withdrawSuccess(amount);
			case NO_ACCOUNT -> msg = messages.noAccount();
			case INSUFFICIENT_CASH -> msg = messages.insufficientCash(amount);
			case INSUFFICIENT_BANK_FUNDS -> msg = messages.insufficientBankFunds(amount);
			case DAILY_DEPOSIT_REACHED -> msg = messages.dailyDepositReached(settings.getDailyDepositLimit());
			case DAILY_WITHDRAW_REACHED -> msg = messages.dailyWithdrawReached(settings.getDailyWithdrawLimit());
			case CAP_EXCEEDED -> {
				BankerSnapshot snap = economy.snapshot(viewer);
				double         cap  = snap.currentTier() == null ? 0 : snap.currentTier().maxBalance();
				msg = messages.capExceeded(cap);
			}
			default -> msg = null;
		}
		if (msg != null) viewer.sendMessage(msg);

		viewer.closeInventory();
		if (menuView != null) {
			Bukkit.getScheduler().runTask(plugin, () -> menuView.open(viewer, session.banker));
		}
	}

	private void cancel(Player viewer, Session session) {
		viewer.closeInventory();
		if (menuView != null) {
			Bukkit.getScheduler().runTask(plugin, () -> menuView.open(viewer, session.banker));
		}
	}

	private void openAmountAnvil(Player viewer, Session session) {
		session.expectingSubview = true;

		AnvilGUI.Builder builder = new AnvilGUI.Builder();
		builder.plugin(plugin)
		       .title("Set Amount")
		       .itemLeft(material(XMaterial.PAPER, Material.PAPER))
		       .text(String.valueOf((long) session.amount))
		       .onClick((slot, state) -> {
				   if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

				   String raw = state.getText() == null ? "" : state.getText().trim();
				   try {
					   double value = Double.parseDouble(raw);
					   if (value < 0) {
						   viewer.sendMessage(ChatUtil.color("&cAmount must be non-negative."));
						   return Collections.emptyList();
					   }
					   session.amount = Math.min(Math.floor(value), session.maxAmount);
					   if (value > session.maxAmount) {
						   viewer.sendMessage(ChatUtil.color(
								   "&eAmount capped at &f$" + NumberUtil.valueFormat(session.maxAmount) + "&e."));
					   }
					   return List.of(AnvilGUI.ResponseAction.close());
				   } catch (NumberFormatException e) {
					   viewer.sendMessage(ChatUtil.color("&cInvalid number: " + raw));
					   return Collections.emptyList();
				   }
			   })
		       .onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> reopenAfterAnvil(viewer)))
		       .open(viewer);
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	public enum Mode {
		DEPOSIT,
		WITHDRAW
	}

	static final class Session {
		final BankerNpc banker;
		final Mode      mode;
		final double    maxAmount;

		InventoryHandler handler;
		double           amount;
		int              modeIndex        = 0;
		boolean          expectingSubview = false;

		Session(BankerNpc banker, Mode mode, double maxAmount, double initial) {
			this.banker    = banker;
			this.mode      = mode;
			this.maxAmount = maxAmount;
			this.amount    = Math.clamp(initial, 0, maxAmount);
		}
	}

}
