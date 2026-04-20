package me.luckyraven.copsncrooks.npc.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.banker.BankerNpc;
import me.luckyraven.copsncrooks.npc.banker.config.BankerSettings;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract.DeletionInfo;
import me.luckyraven.copsncrooks.npc.banker.message.BankerMessageContract;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

@RequiredArgsConstructor
public final class BankerDeleteAccountView {

	private static final int SIZE         = 27;
	private static final int SLOT_INFO    = 4;
	private static final int SLOT_CONFIRM = 11;
	private static final int SLOT_CANCEL  = 15;

	private static final SoundConfiguration SOUND_CONFIRM = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "ENTITY_ITEM_BREAK", 1.0f, 0.8f);
	private static final SoundConfiguration SOUND_CANCEL  = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "UI_BUTTON_CLICK", 0.8f, 0.8f);
	private static final SoundConfiguration SOUND_DENY    = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "ENTITY_VILLAGER_NO", 0.8f, 1.0f);

	private final JavaPlugin            plugin;
	private final BankerSettings        settings;
	private final BankerEconomyContract economy;
	private final BankerMessageContract messages;
	private       BankerMenuView        menuView;

	public void setMenuView(BankerMenuView menuView) {
		this.menuView = menuView;
	}

	public void open(Player viewer, BankerNpc banker) {
		DeletionInfo info = economy.deletionInfo(viewer);
		if (!info.hasAccount()) {
			viewer.sendMessage(messages.noAccount());
			SOUND_DENY.playSound(viewer);
			if (menuView != null) Bukkit.getScheduler().runTask(plugin, () -> menuView.open(viewer, banker));
			return;
		}

		String name  = banker.getData().getDisplayName() != null ? banker.getData().getDisplayName() : "Banker";
		String title = "&8&l[&b&l" + name + "&8&l] &cClose Account";

		InventoryHandler handler = new InventoryHandler(plugin, title, SIZE, viewer);

		boolean canAfford = info.bankBalance() >= info.deleteFee();

		ItemBuilder infoItem = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		infoItem.setDisplayName("&c&lClose your bank account")
		        .setLore("&7Account: &f" + (info.accountName() == null ? "(unnamed)" : info.accountName()),
		                 "&7Current balance: &f$" + NumberUtil.valueFormat(info.bankBalance()),
		                 "&7Closing fee: &c$" + NumberUtil.valueFormat(info.deleteFee()),
		                 "&7Net refund: &a$" + NumberUtil.valueFormat(Math.max(0, info.refund())),
		                 " ",
		                 "&8Bank balance - closing fee + half the creation fee goes to your cash.",
		                 canAfford ? "&cThis cannot be undone."
		                           : "&cBank balance must cover the closing fee.");
		handler.setItem(SLOT_INFO, infoItem, false, (p, inv, b) -> { });

		ItemBuilder confirm = new ItemBuilder(material(
				canAfford ? XMaterial.RED_WOOL : XMaterial.BARRIER,
				canAfford ? Material.RED_WOOL : Material.BARRIER));
		confirm.setDisplayName(canAfford ? "&c&lCLOSE ACCOUNT" : "&7&lCANNOT CLOSE")
		       .setLore(canAfford
		                ? List.of("&7Receive &a$" + NumberUtil.valueFormat(Math.max(0, info.refund())) +
		                          " &7in cash.")
		                : List.of("&cBank balance is below the closing fee."));
		handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> {
			if (!canAfford) {
				SOUND_DENY.playSound(p);
				p.sendMessage(messages.insufficientBankFunds(info.deleteFee()));
				return;
			}
			performDelete(p, banker, info);
		});

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.LIME_WOOL, Material.GREEN_WOOL));
		cancel.setDisplayName("&aKEEP ACCOUNT");
		handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> {
			SOUND_CANCEL.playSound(p);
			p.closeInventory();
			if (menuView != null) Bukkit.getScheduler().runTask(plugin, () -> menuView.open(p, banker));
		});

		InventoryUtil.fillInventory(handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		handler.open(viewer);
	}

	private void performDelete(Player viewer, BankerNpc banker, DeletionInfo preview) {
		BankerEconomyContract.Result result = economy.tryDeleteAccount(viewer);
		String                       msg;
		switch (result) {
			case SUCCESS -> {
				msg = messages.deleteSuccess(preview.accountName() == null ? "account" : preview.accountName(),
				                             preview.refund());
				SOUND_CONFIRM.playSound(viewer);
			}
			case NO_ACCOUNT -> msg = messages.noAccount();
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
