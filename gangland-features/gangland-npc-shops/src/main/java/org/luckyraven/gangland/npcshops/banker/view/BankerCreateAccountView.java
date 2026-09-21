package org.luckyraven.gangland.npcshops.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.npcshops.banker.config.BankerSettings;
import org.luckyraven.gangland.npcshops.banker.economy.BankerEconomyContract;
import org.luckyraven.gangland.npcshops.banker.economy.BankerEconomyContract.CreationInfo;
import org.luckyraven.gangland.npcshops.banker.message.BankerMessageContract;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.NumberUtil;

import java.util.Collections;
import java.util.List;

/**
 * "Open account" panel — shown when the menu detects no account on file. Clicking CONFIRM suspends the flow and pops an
 * AnvilGUI for the account name; the anvil's {@code onClose} resumes the flow and returns to the menu regardless of
 * success/failure, matching the legacy view's behaviour.
 */
@RequiredArgsConstructor
public final class BankerCreateAccountView implements Panel<BankerFlowSession> {

	private static final int ROWS         = 3;
	private static final int SLOT_INFO    = 4;
	private static final int SLOT_CONFIRM = 11;
	private static final int SLOT_CANCEL  = 15;

	private static final SoundEffect SOUND_CONFIRM = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                               "ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f);
	private static final SoundEffect SOUND_DENY    = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                               "ENTITY_VILLAGER_NO", 0.8f, 1.0f);
	private static final SoundEffect SOUND_CLICK   = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                               "UI_BUTTON_CLICK", 0.6f, 1.2f);

	private final JavaPlugin            plugin;
	private final BankerSettings        settings;
	private final BankerEconomyContract economy;
	private final BankerMessageContract messages;

	@Override
	public int rows(BankerFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(BankerFlowSession session) {
		return "&8&l[&b&l" + session.displayName() + "&8&l] &7Open Account";
	}

	@Override
	public void render(MenuFlow<BankerFlowSession> flow, ChestMenuBuilder builder, BankerFlowSession session) {
		CreationInfo info = economy.creationInfo(flow.viewer());

		if (info.hasAccount()) {
			ItemBuilder stub = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER));
			stub.setDisplayName("&7Already have an account")
			    .setLore("&8Use the menu instead of opening another.");
			builder.slot(SLOT_INFO, ItemComponent.of(stub));

			ItemBuilder back = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL)).setDisplayName(
					"&cBACK");
			builder.slot(SLOT_CANCEL, ItemComponent.of(back).onAnyClick(ctx -> {
				flow.back();
				playSoundNextTick(ctx.player(), SOUND_DENY);
			}));
			builder.fill(FillComponent.of(materialOf(settings.getInventoryFillItem())).name(settings.getInventoryFillName()));
			return;
		}

		ItemBuilder infoItem = new ItemBuilder(material(XMaterial.WRITABLE_BOOK, Material.WRITABLE_BOOK));
		infoItem.setDisplayName("&b&lOpen a new bank account")
		        .setLore("&7Fee: &6$" + NumberUtil.valueFormat(info.fee()),
		                 "&7Starting balance: &a$" + NumberUtil.valueFormat(info.initialBalance()),
		                 "&7Your cash: &f$" + NumberUtil.valueFormat(info.cashBalance()),
		                 " ",
		                 info.canAfford() ? "&aYou can afford this." : "&cYou don't have enough cash.");
		builder.slot(SLOT_INFO, ItemComponent.of(infoItem));

		ItemBuilder confirm = new ItemBuilder(material(XMaterial.LIME_WOOL, Material.GREEN_WOOL));
		confirm.setDisplayName("&a&lOPEN ACCOUNT")
		       .setLore("&7Click to choose a name and confirm.", "&7An anvil will open next.");
		builder.slot(SLOT_CONFIRM, ItemComponent.of(confirm).onAnyClick(ctx -> {
			if (!info.canAfford()) {
				ctx.player().sendMessage(messages.createCannotAfford(info.fee()));
				playSoundNextTick(ctx.player(), SOUND_DENY);
				return;
			}
			openNameAnvil(flow, ctx.player());
			playSoundNextTick(ctx.player(), SOUND_CLICK);
		}));

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cCANCEL");
		builder.slot(SLOT_CANCEL, ItemComponent.of(cancel).onAnyClick(ctx -> flow.back()));

		builder.fill(FillComponent.of(materialOf(settings.getInventoryFillItem())).name(settings.getInventoryFillName()));
	}

	/**
	 * Suspends the flow, opens the anvil for the account name. On anvil-close (either successful create or user
	 * escape), resumes the flow and switches back to the menu — matches the legacy view's behaviour of always returning
	 * to the menu after the anvil closes.
	 */
	private void openNameAnvil(MenuFlow<BankerFlowSession> flow, Player viewer) {
		flow.suspend();
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
					flow.resume();
					flow.switchTo(BankerFlowSession.PANEL_MENU);
				}))
				.open(viewer);
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}

	private void playSoundNextTick(Player player, SoundEffect sound) {
		Bukkit.getScheduler().runTask(plugin, () -> sound.playSound(player));
	}

}
