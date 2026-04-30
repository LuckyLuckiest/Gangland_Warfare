package org.luckyraven.gangland.copsncrooks.npc.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.banker.config.BankerSettings;
import org.luckyraven.gangland.copsncrooks.npc.banker.economy.BankerEconomyContract;
import org.luckyraven.gangland.copsncrooks.npc.banker.economy.BankerEconomyContract.CreationInfo;
import org.luckyraven.gangland.copsncrooks.npc.banker.message.BankerMessageContract;
import org.luckyraven.gangland.core.ItemBuilder;
import org.luckyraven.gangland.core.configuration.SoundConfiguration;
import org.luckyraven.gangland.core.utilities.NumberUtil;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.inventory.flow.Panel;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.inventory.util.InventoryUtil;

import java.util.Collections;
import java.util.List;

/**
 * "Open account" panel — shown when the menu detects no account on file. Clicking CONFIRM suspends the flow and pops an
 * AnvilGUI for the account name; the anvil's {@code onClose} resumes the flow and returns to the menu regardless of
 * success/failure, matching the legacy view's behaviour.
 */
@RequiredArgsConstructor
public final class BankerCreateAccountView implements Panel<BankerFlowSession> {

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

	@Override
	public int size(BankerFlowSession session) {
		return SIZE;
	}

	@Override
	public String title(BankerFlowSession session) {
		return "&8&l[&b&l" + session.displayName() + "&8&l] &7Open Account";
	}

	@Override
	public void render(MultiPanelInventory<BankerFlowSession> host, InventoryHandler handler, Player viewer,
	                   BankerFlowSession session) {
		CreationInfo info = economy.creationInfo(viewer);

		if (info.hasAccount()) {
			ItemBuilder stub = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER));
			stub.setDisplayName("&7Already have an account")
			    .setLore("&8Use the menu instead of opening another.");
			handler.setItem(SLOT_INFO, stub, false, (p, inv, b) -> { });

			ItemBuilder back = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL)).setDisplayName(
					"&cBACK");
			handler.setItem(SLOT_CANCEL, back, false, (p, inv, b) -> {
				host.back();
				playSoundNextTick(p, SOUND_DENY);
			});
			InventoryUtil.fillInventory(handler,
			                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));
			return;
		}

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
		       .setLore("&7Click to choose a name and confirm.", "&7An anvil will open next.");
		handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> {
			if (!info.canAfford()) {
				viewer.sendMessage(messages.createCannotAfford(info.fee()));
				playSoundNextTick(viewer, SOUND_DENY);
				return;
			}
			openNameAnvil(host, p);
			playSoundNextTick(p, SOUND_CLICK);
		});

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cCANCEL");
		handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> host.back());

		InventoryUtil.fillInventory(handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));
	}

	/**
	 * Suspends the flow, opens the anvil for the account name. On anvil-close (either successful create or user
	 * escape), resumes the flow and switches back to the menu — matches the legacy view's behaviour of always returning
	 * to the menu after the anvil closes.
	 */
	private void openNameAnvil(MultiPanelInventory<BankerFlowSession> host, Player viewer) {
		host.suspend();
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
							playSoundNextTick(viewer, SOUND_CONFIRM);
							yield messages.createSuccess(text);
						}
						case ALREADY_HAS_ACCOUNT -> messages.createAlreadyHasAccount();
						case CANNOT_AFFORD_CREATION -> messages.createCannotAfford(economy.creationInfo(viewer).fee());
						case NAME_EMPTY -> messages.createNameEmpty();
						default -> null;
					};
					if (msg != null) viewer.sendMessage(msg);

					return List.of(AnvilGUI.ResponseAction.close());
				})
				.onClose(state -> Bukkit.getScheduler().runTask(plugin, () -> {
					host.resume();
					host.switchTo(BankerFlowSession.PANEL_MENU);
				}))
				.open(viewer);
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private void playSoundNextTick(Player player, SoundConfiguration sound) {
		Bukkit.getScheduler().runTask(plugin, () -> sound.playSound(player));
	}

}
