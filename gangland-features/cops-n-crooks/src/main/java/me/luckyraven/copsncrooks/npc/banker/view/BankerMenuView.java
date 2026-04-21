package me.luckyraven.copsncrooks.npc.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.banker.BankerNpc;
import me.luckyraven.copsncrooks.npc.banker.config.BankerSettings;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract.BankerSnapshot;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract.CreationInfo;
import me.luckyraven.copsncrooks.npc.banker.message.BankerMessageContract;
import me.luckyraven.copsncrooks.npc.banker.tier.BankTier;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public final class BankerMenuView {

	private static final int SIZE               = 27;
	private static final int SLOT_INFO          = 4;
	private static final int SLOT_DEPOSIT       = 10;
	private static final int SLOT_WITHDRAW      = 12;
	private static final int SLOT_UPGRADE       = 14;
	private static final int SLOT_REWARDS       = 16;
	private static final int SLOT_RENAME        = 20;
	private static final int SLOT_CREATE_PROMPT = 13;
	private static final int SLOT_CLOSE         = 22;

	private static final SoundConfiguration SOUND_PICK = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                            "UI_BUTTON_CLICK", 0.6f, 1.4f);
	private static final SoundConfiguration SOUND_DENY = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                            "ENTITY_VILLAGER_NO", 0.8f, 1.0f);

	private final JavaPlugin            plugin;
	private final BankerSettings        settings;
	private final BankerEconomyContract economy;
	private final BankerMessageContract messages;

	private BankerAmountView        amountView;
	private BankerUpgradeView       upgradeView;
	private BankerCreateAccountView createView;
	private BankerRenameAccountView renameView;
	private BankerClaimView         claimView;

	/**
	 * Formats a {@link BigDecimal} amount for lore display via {@link NumberUtil#valueFormat(BigDecimal)} — the
	 * BigDecimal-native path preserves precision for Vault-tier values beyond {@code 2^53}.
	 */
	private static String amount(BigDecimal value) {
		return NumberUtil.valueFormat(value);
	}

	public void setSubViews(BankerAmountView amountView, BankerUpgradeView upgradeView,
	                        BankerCreateAccountView createView, BankerRenameAccountView renameView,
	                        BankerClaimView claimView) {
		this.amountView  = amountView;
		this.upgradeView = upgradeView;
		this.createView  = createView;
		this.renameView  = renameView;
		this.claimView   = claimView;
	}

	// ── No account path ────────────────────────────────────────────────────

	public void open(Player viewer, BankerNpc banker) {
		BankerSnapshot snap = economy.snapshot(viewer);

		String name  = banker.getData().getDisplayName() != null ? banker.getData().getDisplayName() : "Banker";
		String title = "&8&l[&b&l" + name + "&8&l]";

		InventoryHandler handler = new InventoryHandler(plugin, title, SIZE, viewer);

		if (!snap.hasBank()) {
			renderNoAccount(handler, viewer, banker);
		} else {
			renderHasAccount(handler, snap, banker);
		}

		ItemBuilder close = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER)).setDisplayName("&cClose");
		handler.setItem(SLOT_CLOSE, close, false, (p, inv, b) -> p.closeInventory());

		InventoryUtil.fillInventory(handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		handler.open(viewer);
	}

	// ── Existing account path ──────────────────────────────────────────────

	private void renderNoAccount(InventoryHandler handler, Player viewer, BankerNpc banker) {
		CreationInfo info = economy.creationInfo(viewer);

		ItemBuilder infoItem = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		infoItem.setDisplayName("&b&lNo bank account on file")
		        .setLore("&7Open an account to deposit and withdraw.",
		                 "&7Fee: &6$" + amount(info.fee()),
		                 "&7Starting balance: &a$" + amount(info.initialBalance()),
		                 "&7Your cash: &f$" + amount(info.cashBalance()));
		handler.setItem(SLOT_INFO, infoItem, false, (p, inv, b) -> { });

		ItemBuilder create = new ItemBuilder(material(XMaterial.WRITABLE_BOOK, Material.WRITABLE_BOOK));
		create.setDisplayName("&a&lOPEN ACCOUNT")
		      .setLore("&7Pay &6$" + amount(info.fee()) + " &7to start banking.",
		               info.canAfford() ? "&aClick to open." : "&cYou don't have enough cash.");
		handler.setItem(SLOT_CREATE_PROMPT, create, false, (p, inv, b) -> {
			if (!info.canAfford()) {
				viewer.sendMessage(messages.createCannotAfford(info.fee()));
				SOUND_DENY.playSound(viewer);
				return;
			}
			SOUND_PICK.playSound(p);
			p.closeInventory();
			if (createView != null) createView.open(p, banker);
		});
	}

	private void renderHasAccount(InventoryHandler handler, BankerSnapshot snap, BankerNpc banker) {
		ItemBuilder info = new ItemBuilder(material(XMaterial.BOOK, Material.BOOK));
		info.setDisplayName("&b&lBank Account").setLore(buildInfoLore(snap));
		handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });

		ItemBuilder deposit = new ItemBuilder(material(XMaterial.EMERALD_BLOCK, Material.EMERALD_BLOCK));
		deposit.setDisplayName("&a&lDEPOSIT")
		       .setLore("&7Move money from your cash to the bank.",
		                "&7Cash: &f$" + amount(snap.cashBalance()),
		                "&7Daily remaining: &f$" + amount(snap.remainingDailyDeposit()));
		handler.setItem(SLOT_DEPOSIT, deposit, false, (p, inv, b) -> {
			SOUND_PICK.playSound(p);
			p.closeInventory();
			if (amountView != null) amountView.open(p, banker, BankerAmountView.Mode.DEPOSIT);
		});

		ItemBuilder withdraw = new ItemBuilder(material(XMaterial.GOLD_BLOCK, Material.GOLD_BLOCK));
		withdraw.setDisplayName("&6&lWITHDRAW")
		        .setLore("&7Move money from the bank to your cash.",
		                 "&7Bank: &f$" + amount(snap.bankBalance()));
		handler.setItem(SLOT_WITHDRAW, withdraw, false, (p, inv, b) -> {
			SOUND_PICK.playSound(p);
			p.closeInventory();
			if (amountView != null) amountView.open(p, banker, BankerAmountView.Mode.WITHDRAW);
		});

		if (snap.nextTier() != null) {
			ItemBuilder upgrade = new ItemBuilder(material(XMaterial.DIAMOND_BLOCK, Material.DIAMOND_BLOCK));
			upgrade.setDisplayName("&b&lUPGRADE").setLore(buildUpgradeLore(snap));
			handler.setItem(SLOT_UPGRADE, upgrade, false, (p, inv, b) -> {
				SOUND_PICK.playSound(p);
				p.closeInventory();
				if (upgradeView != null) upgradeView.open(p, banker);
			});
		} else {
			ItemBuilder maxTier = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER));
			maxTier.setDisplayName("&7Max Tier Reached").setLore("&8Your bank is at the top of the ladder.");
			handler.setItem(SLOT_UPGRADE, maxTier, false, (p, inv, b) -> SOUND_DENY.playSound(p));
		}

		ItemBuilder rename = new ItemBuilder(material(XMaterial.NAME_TAG, Material.NAME_TAG));
		rename.setDisplayName("&e&lRENAME ACCOUNT")
		      .setLore("&7Change your account display name.",
		               "&7Rename fee: &c$" + amount(settings.getRenameFee()),
		               "&8Opens an anvil GUI.");
		handler.setItem(SLOT_RENAME, rename, false, (p, inv, b) -> {
			SOUND_PICK.playSound(p);
			p.closeInventory();
			if (renameView != null) renameView.open(p, banker);
		});

		ItemBuilder rewards = new ItemBuilder(material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT));
		rewards.setDisplayName("&6&lREWARDS")
		       .setLore("&7Claim free weekly + monthly bonuses.",
		                "&8Available amounts scale with your tier.");
		handler.setItem(SLOT_REWARDS, rewards, false, (p, inv, b) -> {
			SOUND_PICK.playSound(p);
			p.closeInventory();
			if (claimView != null) claimView.open(p, banker);
		});
	}

	private List<String> buildInfoLore(BankerSnapshot snap) {
		List<String> lore = new ArrayList<>();
		lore.add("&7Cash on hand: &a$" + amount(snap.cashBalance()));
		lore.add("&7Bank balance: &a$" + amount(snap.bankBalance()));
		BankTier tier = snap.currentTier();
		if (tier != null) {
			lore.add("&7Tier: " + tier.displayName());
			lore.add("&7Tier cap: &f$" + amount(tier.maxBalance()));
		}
		lore.add(" ");
		if (snap.dailyDepositLimit().signum() > 0) {
			lore.add("&7Daily deposit left: &f$" + amount(snap.remainingDailyDeposit()) + " &8/ $" +
			         amount(snap.dailyDepositLimit()));
		} else {
			lore.add("&7Daily deposit left: &aunlimited");
		}
		if (snap.dailyInterestRate() > 0) {
			double pct = snap.dailyInterestRate() * 100D;
			lore.add("&7Interest: &a+" + formatPercent(pct) + "%/day");
		}
		Instant reset = snap.capResetAt();
		if (reset != null) {
			Duration remaining = Duration.between(Instant.now(), reset);
			if (!remaining.isNegative() && !remaining.isZero()) {
				lore.add("&7Next reset: &f" + formatDuration(remaining));
			}
		}
		return lore;
	}

	private String formatDuration(Duration duration) {
		long totalSec = Math.max(0, duration.getSeconds());
		long days     = totalSec / 86_400;
		long hours    = (totalSec % 86_400) / 3_600;
		long mins     = (totalSec % 3_600) / 60;

		if (days > 0) return days + "d " + hours + "h";
		if (hours > 0) return hours + "h " + mins + "m";
		if (mins > 0) return mins + "m";
		return "<1m";
	}

	private String formatPercent(double pct) {
		if (pct == Math.floor(pct)) return String.valueOf((long) pct);
		return String.format("%.2f", pct);
	}

	private List<String> buildUpgradeLore(BankerSnapshot snap) {
		List<String> lore = new ArrayList<>();
		BankTier     next = snap.nextTier();
		if (next == null) {
			lore.add("&7Already at the top tier.");
			return lore;
		}
		BankTier current = snap.currentTier();
		if (current != null) {
			lore.add("&7Current: " + current.displayName());
		}
		lore.add("&7Next: " + next.displayName());
		lore.add("&7New cap: &f$" + amount(next.maxBalance()));
		lore.add("&7Cost: &6$" + amount(next.upgradeCost()) + " &7(from bank)");
		lore.add(" ");
		if (snap.bankBalance().compareTo(next.upgradeCost()) < 0) {
			lore.add("&cInsufficient bank funds.");
		} else {
			lore.add("&aClick to upgrade.");
		}
		return lore;
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

}
