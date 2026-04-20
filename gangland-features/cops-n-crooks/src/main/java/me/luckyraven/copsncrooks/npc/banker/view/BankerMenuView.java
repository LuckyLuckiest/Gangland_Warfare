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
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.NumberUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public final class BankerMenuView {

	private static final int SIZE               = 27;
	private static final int SLOT_INFO          = 4;
	private static final int SLOT_DEPOSIT       = 10;
	private static final int SLOT_WITHDRAW      = 12;
	private static final int SLOT_UPGRADE       = 14;
	private static final int SLOT_DELETE        = 16;
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
	private BankerDeleteAccountView deleteView;
	private BankerRenameAccountView renameView;

	public void setSubViews(BankerAmountView amountView, BankerUpgradeView upgradeView,
	                        BankerCreateAccountView createView, BankerDeleteAccountView deleteView,
	                        BankerRenameAccountView renameView) {
		this.amountView  = amountView;
		this.upgradeView = upgradeView;
		this.createView  = createView;
		this.deleteView  = deleteView;
		this.renameView  = renameView;
	}

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

	// ── No account path ────────────────────────────────────────────────────

	private void renderNoAccount(InventoryHandler handler, Player viewer, BankerNpc banker) {
		CreationInfo info = economy.creationInfo(viewer);

		ItemBuilder infoItem = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		infoItem.setDisplayName("&b&lNo bank account on file")
		        .setLore("&7Open an account to deposit and withdraw.",
		                 "&7Fee: &6$" + NumberUtil.valueFormat(info.fee()),
		                 "&7Starting balance: &a$" + NumberUtil.valueFormat(info.initialBalance()),
		                 "&7Your cash: &f$" + NumberUtil.valueFormat(info.cashBalance()));
		handler.setItem(SLOT_INFO, infoItem, false, (p, inv, b) -> { });

		ItemBuilder create = new ItemBuilder(material(XMaterial.WRITABLE_BOOK, Material.WRITABLE_BOOK));
		create.setDisplayName("&a&lOPEN ACCOUNT")
		      .setLore("&7Pay &6$" + NumberUtil.valueFormat(info.fee()) + " &7to start banking.",
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

	// ── Existing account path ──────────────────────────────────────────────

	private void renderHasAccount(InventoryHandler handler, BankerSnapshot snap, BankerNpc banker) {
		ItemBuilder info = new ItemBuilder(material(XMaterial.BOOK, Material.BOOK));
		info.setDisplayName("&b&lBank Account").setLore(buildInfoLore(snap));
		handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });

		ItemBuilder deposit = new ItemBuilder(material(XMaterial.EMERALD_BLOCK, Material.EMERALD_BLOCK));
		deposit.setDisplayName("&a&lDEPOSIT")
		       .setLore("&7Move money from your cash to the bank.",
		                "&7Cash: &f$" + NumberUtil.valueFormat(snap.cashBalance()),
		                "&7Daily remaining: &f$" + NumberUtil.valueFormat(snap.remainingDailyDeposit()));
		handler.setItem(SLOT_DEPOSIT, deposit, false, (p, inv, b) -> {
			SOUND_PICK.playSound(p);
			p.closeInventory();
			if (amountView != null) amountView.open(p, banker, BankerAmountView.Mode.DEPOSIT);
		});

		ItemBuilder withdraw = new ItemBuilder(material(XMaterial.GOLD_BLOCK, Material.GOLD_BLOCK));
		withdraw.setDisplayName("&6&lWITHDRAW")
		        .setLore("&7Move money from the bank to your cash.",
		                 "&7Bank: &f$" + NumberUtil.valueFormat(snap.bankBalance()),
		                 "&7Daily remaining: &f$" + NumberUtil.valueFormat(snap.remainingDailyWithdraw()));
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

		ItemBuilder delete = new ItemBuilder(material(XMaterial.TNT, Material.TNT));
		delete.setDisplayName("&c&lCLOSE ACCOUNT")
		      .setLore("&7Close this account and get a refund.",
		               "&7Closing fee: &c$" + NumberUtil.valueFormat(settings.getDeleteFee()),
		               "&8Balance - closing fee + half the creation fee returns to your cash.");
		handler.setItem(SLOT_DELETE, delete, false, (p, inv, b) -> {
			SOUND_PICK.playSound(p);
			p.closeInventory();
			if (deleteView != null) deleteView.open(p, banker);
		});

		ItemBuilder rename = new ItemBuilder(material(XMaterial.NAME_TAG, Material.NAME_TAG));
		rename.setDisplayName("&e&lRENAME ACCOUNT")
		      .setLore("&7Change your account display name.",
		               "&7Rename fee: &c$" + NumberUtil.valueFormat(settings.getRenameFee()), "&8Opens an anvil GUI.");
		handler.setItem(SLOT_RENAME, rename, false, (p, inv, b) -> {
			SOUND_PICK.playSound(p);
			p.closeInventory();
			if (renameView != null) renameView.open(p, banker);
		});
	}

	private List<String> buildInfoLore(BankerSnapshot snap) {
		List<String> lore = new ArrayList<>();
		lore.add("&7Cash on hand: &a$" + NumberUtil.valueFormat(snap.cashBalance()));
		lore.add("&7Bank balance: &a$" + NumberUtil.valueFormat(snap.bankBalance()));
		BankTier tier = snap.currentTier();
		if (tier != null) {
			lore.add("&7Tier: " + tier.displayName());
			lore.add("&7Tier cap: &f$" + NumberUtil.valueFormat(tier.maxBalance()));
		}
		lore.add(" ");
		lore.add("&7Daily deposit left: &f$" + NumberUtil.valueFormat(snap.remainingDailyDeposit()) + " &8/ $" +
		         NumberUtil.valueFormat(snap.dailyDepositLimit()));
		lore.add("&7Daily withdraw left: &f$" + NumberUtil.valueFormat(snap.remainingDailyWithdraw()) + " &8/ $" +
		         NumberUtil.valueFormat(snap.dailyWithdrawLimit()));
		return lore;
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
		lore.add("&7New cap: &f$" + NumberUtil.valueFormat(next.maxBalance()));
		lore.add("&7Cost: &6$" + NumberUtil.valueFormat(next.upgradeCost()) + " &7(from bank)");
		lore.add(" ");
		if (snap.bankBalance() < next.upgradeCost()) {
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
