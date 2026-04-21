package me.luckyraven.copsncrooks.npc.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.luckyraven.copsncrooks.npc.banker.BankerNpc;
import me.luckyraven.copsncrooks.npc.banker.config.BankerSettings;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract.BankerSnapshot;
import me.luckyraven.copsncrooks.npc.banker.message.BankerMessageContract;
import me.luckyraven.copsncrooks.npc.banker.tier.BankTier;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;

@RequiredArgsConstructor
public final class BankerUpgradeView {

	private static final int SIZE         = 27;
	private static final int SLOT_CURRENT = 11;
	private static final int SLOT_ARROW   = 13;
	private static final int SLOT_NEXT    = 15;
	private static final int SLOT_CANCEL  = 20;
	private static final int SLOT_CONFIRM = 24;

	private static final SoundConfiguration SOUND_CONFIRM = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f);
	private static final SoundConfiguration SOUND_DENY    = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "ENTITY_VILLAGER_NO", 0.8f, 1.0f);
	private static final SoundConfiguration SOUND_CANCEL  = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "UI_BUTTON_CLICK", 0.8f, 0.8f);

	private final JavaPlugin            plugin;
	private final BankerSettings        settings;
	private final BankerEconomyContract economy;
	private final BankerMessageContract messages;

	@Setter
	private BankerMenuView menuView;

	private static String format(BigDecimal value) {
		return NumberUtil.valueFormat(value);
	}

	public void open(Player viewer, BankerNpc banker) {
		BankerSnapshot snap = economy.snapshot(viewer);
		if (!snap.hasBank()) {
			viewer.sendMessage(messages.noAccount());
			return;
		}
		BankTier next = snap.nextTier();
		if (next == null) {
			viewer.sendMessage(messages.upgradeMaxTier());
			SOUND_DENY.playSound(viewer);
			if (menuView != null) Bukkit.getScheduler().runTask(plugin, () -> menuView.open(viewer, banker));
			return;
		}

		String name  = banker.getData().getDisplayName() != null ? banker.getData().getDisplayName() : "Banker";
		String title = "&8&l[&b&l" + name + "&8&l] &7Upgrade";

		InventoryHandler handler = new InventoryHandler(plugin, title, SIZE, viewer);

		BankTier    current     = snap.currentTier();
		ItemBuilder currentItem = new ItemBuilder(material(XMaterial.IRON_BLOCK, Material.IRON_BLOCK));
		currentItem.setDisplayName("&7Current: " + (current != null ? current.displayName() : "&8None"))
		           .setLore("&7Cap: &f$" + format(current != null ? current.maxBalance() : null),
		                    "&7Balance: &f$" + format(snap.bankBalance()));
		handler.setItem(SLOT_CURRENT, currentItem, false, (p, inv, b) -> { });

		ItemBuilder arrow = new ItemBuilder(material(XMaterial.ARROW, Material.ARROW));
		arrow.setDisplayName("&e→ Upgrade").setLore("&7Pay &6$" + format(next.upgradeCost()));
		handler.setItem(SLOT_ARROW, arrow, false, (p, inv, b) -> { });

		ItemBuilder nextItem = new ItemBuilder(material(XMaterial.DIAMOND_BLOCK, Material.DIAMOND_BLOCK));
		nextItem.setDisplayName("&aNext: " + next.displayName())
		        .setLore("&7New cap: &f$" + format(next.maxBalance()),
		                 "&7Upgrade cost: &6$" + format(next.upgradeCost()));
		handler.setItem(SLOT_NEXT, nextItem, false, (p, inv, b) -> { });

		ItemBuilder confirm = new ItemBuilder(material(XMaterial.LIME_WOOL, Material.GREEN_WOOL));
		confirm.setDisplayName("&a&lCONFIRM UPGRADE")
		       .setLore("&7Pay &6$" + format(next.upgradeCost()) + " &7from your bank.");
		handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> performUpgrade(p, banker, next));

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cCANCEL");
		handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> {
			SOUND_CANCEL.playSound(p);
			p.closeInventory();
			if (menuView != null) Bukkit.getScheduler().runTask(plugin, () -> menuView.open(p, banker));
		});

		InventoryUtil.fillInventory(handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		handler.open(viewer);
	}

	private void performUpgrade(Player viewer, BankerNpc banker, BankTier next) {
		BankerEconomyContract.Result result = economy.tryUpgrade(viewer);
		String                       msg;
		switch (result) {
			case SUCCESS -> {
				msg = messages.upgradeSuccess(next.displayName());
				SOUND_CONFIRM.playSound(viewer);
			}
			case ALREADY_MAX_TIER -> {
				msg = messages.upgradeMaxTier();
				SOUND_DENY.playSound(viewer);
			}
			case INSUFFICIENT_BANK_FUNDS -> {
				msg = messages.upgradeInsufficientFunds(next.upgradeCost());
				SOUND_DENY.playSound(viewer);
			}
			case NO_ACCOUNT -> msg = messages.noAccount();
			case TIER_MISSING -> msg = messages.tierMissing();
			default -> msg = null;
		}
		if (msg != null) viewer.sendMessage(msg);

		viewer.closeInventory();
		if (menuView != null) Bukkit.getScheduler().runTask(plugin, () -> menuView.open(viewer, banker));
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

}
