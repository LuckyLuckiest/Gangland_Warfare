package org.luckyraven.gangland.npcshops.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.npcshops.banker.config.BankerSettings;
import org.luckyraven.gangland.npcshops.banker.economy.BankerEconomyContract;
import org.luckyraven.gangland.npcshops.banker.economy.BankerEconomyContract.BankerSnapshot;
import org.luckyraven.gangland.npcshops.banker.economy.BankerEconomyContract.CreationInfo;
import org.luckyraven.gangland.npcshops.banker.message.BankerMessageContract;
import org.luckyraven.gangland.npcshops.banker.tier.BankTier;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.NumberUtil;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The banker flow's root panel — the rich account menu. Every action button now pivots to another panel via
 * {@link MenuFlow#switchTo(String)} (amount / upgrade / claim / create) or kicks off an anvil prompt that returns to
 * this panel on close (rename). No more legacy {@code player.closeInventory() + subview.open(...)} hops.
 */
@RequiredArgsConstructor
public final class BankerMenuView implements Panel<BankerFlowSession> {

	private static final int ROWS               = 3;
	private static final int SLOT_INFO          = 4;
	private static final int SLOT_DEPOSIT       = 10;
	private static final int SLOT_WITHDRAW      = 12;
	private static final int SLOT_UPGRADE       = 14;
	private static final int SLOT_REWARDS       = 16;
	private static final int SLOT_RENAME        = 20;
	private static final int SLOT_CREATE_PROMPT = 13;
	private static final int SLOT_CLOSE         = 22;

	private static final SoundEffect SOUND_PICK = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                            "UI_BUTTON_CLICK", 0.6f, 1.4f);
	private static final SoundEffect SOUND_DENY = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                            "ENTITY_VILLAGER_NO", 0.8f, 1.0f);

	private final JavaPlugin            plugin;
	private final BankerSettings        settings;
	private final BankerEconomyContract economy;
	private final BankerMessageContract messages;

	private BankerRenameAccountView renameView;

	private static String amount(BigDecimal value) {
		return NumberUtil.valueFormat(value);
	}

	public void setSubViews(BankerRenameAccountView renameView) {
		this.renameView = renameView;
	}

	@Override
	public int rows(BankerFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(BankerFlowSession session) {
		return "&8&l[&b&l" + session.displayName() + "&8&l]";
	}

	@Override
	public void render(MenuFlow<BankerFlowSession> flow, ChestMenuBuilder builder, BankerFlowSession session) {
		BankerSnapshot snap = economy.snapshot(flow.viewer());

		if (!snap.hasBank()) {
			renderNoAccount(flow, builder, session);
		} else {
			renderHasAccount(flow, builder, snap, session);
		}

		ItemBuilder close = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER)).setDisplayName("&cClose");
		builder.slot(SLOT_CLOSE, ItemComponent.of(close).onAnyClick(ctx -> flow.end()));

		builder.fill(FillComponent.of(materialOf(settings.getInventoryFillItem())).name(settings.getInventoryFillName()));
	}

	// ── No account path ────────────────────────────────────────────────────

	private void renderNoAccount(MenuFlow<BankerFlowSession> flow, ChestMenuBuilder builder,
	                             BankerFlowSession session) {
		CreationInfo info = economy.creationInfo(flow.viewer());

		ItemBuilder infoItem = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		infoItem.setDisplayName("&b&lNo bank account on file")
		        .setLore("&7Open an account to deposit and withdraw.",
		                 "&7Fee: &6$" + amount(info.fee()),
		                 "&7Starting balance: &a$" + amount(info.initialBalance()),
		                 "&7Your cash: &f$" + amount(info.cashBalance()));
		builder.slot(SLOT_INFO, ItemComponent.of(infoItem));

		ItemBuilder create = new ItemBuilder(material(XMaterial.WRITABLE_BOOK, Material.WRITABLE_BOOK));
		create.setDisplayName("&a&lOPEN ACCOUNT")
		      .setLore("&7Pay &6$" + amount(info.fee()) + " &7to start banking.",
		               info.canAfford() ? "&aClick to open." : "&cYou don't have enough cash.");
		builder.slot(SLOT_CREATE_PROMPT, ItemComponent.of(create).onAnyClick(ctx -> {
			if (!info.canAfford()) {
				ctx.player().sendMessage(messages.createCannotAfford(info.fee()));
				Bukkit.getScheduler().runTask(plugin, () -> SOUND_DENY.playSound(ctx.player()));
				return;
			}
			flow.switchTo(BankerFlowSession.PANEL_CREATE);
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_PICK.playSound(ctx.player()));
		}));
	}

	// ── Existing account path ──────────────────────────────────────────────

	private void renderHasAccount(MenuFlow<BankerFlowSession> flow, ChestMenuBuilder builder, BankerSnapshot snap,
	                              BankerFlowSession session) {
		ItemBuilder info = new ItemBuilder(material(XMaterial.BOOK, Material.BOOK));
		info.setDisplayName("&b&lBank Account").setLore(buildInfoLore(snap));
		builder.slot(SLOT_INFO, ItemComponent.of(info));

		ItemBuilder deposit = new ItemBuilder(material(XMaterial.EMERALD_BLOCK, Material.EMERALD_BLOCK));
		deposit.setDisplayName("&a&lDEPOSIT")
		       .setLore("&7Move money from your cash to the bank.",
		                "&7Cash: &f$" + amount(snap.cashBalance()),
		                "&7Daily remaining: &f$" + amount(snap.remainingDailyDeposit()));
		builder.slot(SLOT_DEPOSIT, ItemComponent.of(deposit).onAnyClick(ctx -> {
			session.amountMode      = BankerAmountView.Mode.DEPOSIT;
			session.amountStaged    = null;
			session.amountStepIndex = 0;
			flow.switchTo(BankerFlowSession.PANEL_AMOUNT);
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_PICK.playSound(ctx.player()));
		}));

		ItemBuilder withdraw = new ItemBuilder(material(XMaterial.GOLD_BLOCK, Material.GOLD_BLOCK));
		withdraw.setDisplayName("&6&lWITHDRAW")
		        .setLore("&7Move money from the bank to your cash.",
		                 "&7Bank: &f$" + amount(snap.bankBalance()));
		builder.slot(SLOT_WITHDRAW, ItemComponent.of(withdraw).onAnyClick(ctx -> {
			session.amountMode      = BankerAmountView.Mode.WITHDRAW;
			session.amountStaged    = null;
			session.amountStepIndex = 0;
			flow.switchTo(BankerFlowSession.PANEL_AMOUNT);
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_PICK.playSound(ctx.player()));
		}));

		if (snap.nextTier() != null) {
			ItemBuilder upgrade = new ItemBuilder(material(XMaterial.DIAMOND_BLOCK, Material.DIAMOND_BLOCK));
			upgrade.setDisplayName("&b&lUPGRADE").setLore(buildUpgradeLore(snap));
			builder.slot(SLOT_UPGRADE, ItemComponent.of(upgrade).onAnyClick(ctx -> {
				flow.switchTo(BankerFlowSession.PANEL_UPGRADE);
				Bukkit.getScheduler().runTask(plugin, () -> SOUND_PICK.playSound(ctx.player()));
			}));
		} else {
			ItemBuilder maxTier = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER));
			maxTier.setDisplayName("&7Max Tier Reached").setLore("&8Your bank is at the top of the ladder.");
			builder.slot(SLOT_UPGRADE, ItemComponent.of(maxTier).onAnyClick(ctx -> SOUND_DENY.playSound(ctx.player())));
		}

		ItemBuilder rename = new ItemBuilder(material(XMaterial.NAME_TAG, Material.NAME_TAG));
		rename.setDisplayName("&e&lRENAME ACCOUNT")
		      .setLore("&7Change your account display name.",
		               "&7Rename fee: &c$" + amount(settings.getRenameFee()),
		               "&8Opens an anvil GUI.");
		builder.slot(SLOT_RENAME, ItemComponent.of(rename).onAnyClick(ctx -> {
			// Rename is an anvil-only prompt (not a panel). It suspends the flow internally, opens the anvil, and on
			// anvil-close switches back to PANEL_MENU — no switchTo here.
			if (renameView != null) renameView.open(flow, ctx.player());
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_PICK.playSound(ctx.player()));
		}));

		ItemBuilder rewards = new ItemBuilder(material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT));
		rewards.setDisplayName("&6&lREWARDS")
		       .setLore("&7Claim free weekly + monthly bonuses.",
		                "&8Available amounts scale with your tier.");
		builder.slot(SLOT_REWARDS, ItemComponent.of(rewards).onAnyClick(ctx -> {
			flow.switchTo(BankerFlowSession.PANEL_CLAIM);
			Bukkit.getScheduler().runTask(plugin, () -> SOUND_PICK.playSound(ctx.player()));
		}));
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
		if (current != null) lore.add("&7Current: " + current.displayName());
		lore.add("&7Next: " + next.displayName());
		lore.add("&7New cap: &f$" + amount(next.maxBalance()));
		lore.add("&7Cost: &6$" + amount(next.upgradeCost()) + " &7(from bank)");
		lore.add(" ");
		if (snap.bankBalance().compareTo(next.upgradeCost()) < 0) lore.add("&cInsufficient bank funds.");
		else lore.add("&aClick to upgrade.");
		return lore;
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}

}
