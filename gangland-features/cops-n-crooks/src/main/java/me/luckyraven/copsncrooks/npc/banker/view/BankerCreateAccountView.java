package me.luckyraven.copsncrooks.npc.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.banker.BankerNpc;
import me.luckyraven.copsncrooks.npc.banker.config.BankerSettings;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract.CreationInfo;
import me.luckyraven.copsncrooks.npc.banker.message.BankerMessageContract;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public final class BankerCreateAccountView {

	private static final int SIZE         = 27;
	private static final int SLOT_INFO    = 4;
	private static final int SLOT_CONFIRM = 11;
	private static final int SLOT_CANCEL  = 15;

	private static final SoundConfiguration SOUND_CONFIRM = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f);
	private static final SoundConfiguration SOUND_DENY    = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "ENTITY_VILLAGER_NO", 0.8f, 1.0f);
	private static final SoundConfiguration SOUND_CLICK   = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "UI_BUTTON_CLICK", 0.6f, 1.2f);

	private final JavaPlugin            plugin;
	private final BankerSettings        settings;
	private final BankerEconomyContract economy;
	private final BankerMessageContract messages;
	private       BankerMenuView        menuView;

	public void setMenuView(BankerMenuView menuView) {
		this.menuView = menuView;
	}

	public void open(Player viewer, BankerNpc banker) {
		CreationInfo info = economy.creationInfo(viewer);
		if (info.hasAccount()) {
			viewer.sendMessage(messages.createAlreadyHasAccount());
			SOUND_DENY.playSound(viewer);
			if (menuView != null) Bukkit.getScheduler().runTask(plugin, () -> menuView.open(viewer, banker));
			return;
		}

		String name  = banker.getData().getDisplayName() != null ? banker.getData().getDisplayName() : "Banker";
		String title = "&8&l[&b&l" + name + "&8&l] &7Open Account";

		InventoryHandler handler = new InventoryHandler(plugin, title, SIZE, viewer);

		ItemBuilder infoItem = new ItemBuilder(material(XMaterial.WRITABLE_BOOK, Material.WRITABLE_BOOK));
		infoItem.setDisplayName("&b&lOpen a new bank account")
		        .setLore("&7Fee: &6$" + NumberUtil.valueFormat(info.fee()),
		                 "&7Starting balance: &a$" + NumberUtil.valueFormat(info.initialBalance()),
		                 "&7Your cash: &f$" + NumberUtil.valueFormat(info.cashBalance()),
		                 " ",
		                 info.canAfford() ? "&aYou can afford this." : "&cYou don't have enough cash.");
		handler.setItem(SLOT_INFO, infoItem, false, (p, inv, b) -> { });

		ItemBuilder confirm = new ItemBuilder(material(XMaterial.LIME_WOOL, Material.GREEN_WOOL));
		confirm.setDisplayName("&a&lOPEN ACCOUNT")
		       .setLore("&7Click to choose a name and confirm.",
		                "&7An anvil will open next.");
		handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> {
			if (!info.canAfford()) {
				viewer.sendMessage(messages.createCannotAfford(info.fee()));
				SOUND_DENY.playSound(viewer);
				return;
			}
			SOUND_CLICK.playSound(p);
			p.closeInventory();
			Bukkit.getScheduler().runTask(plugin, () -> openNameAnvil(p, banker));
		});

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cCANCEL");
		handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> {
			p.closeInventory();
			if (menuView != null) Bukkit.getScheduler().runTask(plugin, () -> menuView.open(p, banker));
		});

		InventoryUtil.fillInventory(handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		handler.open(viewer);
	}

	private void openNameAnvil(Player viewer, BankerNpc banker) {
		new AnvilGUI.Builder()
				.plugin(plugin)
				.title("Account Name")
				.itemLeft(new ItemStack(Material.NAME_TAG))
				.text("")
				.onClick((slot, state) -> {
					if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

					String text = state.getText() == null ? "" : state.getText().trim();
					if (text.isBlank()) {
						viewer.sendMessage(messages.createNameEmpty());
						return Collections.emptyList();
					}

					BankerEconomyContract.Result result = economy.tryCreateAccount(viewer, text);
					String msg = switch (result) {
						case SUCCESS -> {
							SOUND_CONFIRM.playSound(viewer);
							yield messages.createSuccess(text);
						}
						case ALREADY_HAS_ACCOUNT -> messages.createAlreadyHasAccount();
						case CANNOT_AFFORD_CREATION -> messages.createCannotAfford(info(viewer).fee());
						case NAME_EMPTY -> messages.createNameEmpty();
						default -> null;
					};
					if (msg != null) viewer.sendMessage(msg);

					return List.of(AnvilGUI.ResponseAction.close());
				})
				.onClose(state -> {
					if (menuView != null) Bukkit.getScheduler().runTask(plugin, () -> menuView.open(viewer, banker));
				})
				.open(viewer);
	}

	private CreationInfo info(Player viewer) {
		return economy.creationInfo(viewer);
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

}
