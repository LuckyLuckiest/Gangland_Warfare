package me.luckyraven.copsncrooks.npc.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.banker.BankerNpc;
import me.luckyraven.copsncrooks.npc.banker.config.BankerSettings;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract.RenameInfo;
import me.luckyraven.copsncrooks.npc.banker.message.BankerMessageContract;
import me.luckyraven.util.configuration.SoundConfiguration;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public final class BankerRenameAccountView {

	private static final SoundConfiguration SOUND_CONFIRM = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "ENTITY_PLAYER_LEVELUP", 1.0f, 1.2f);
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
		RenameInfo info = economy.renameInfo(viewer);
		if (!info.hasAccount()) {
			viewer.sendMessage(messages.noAccount());
			SOUND_DENY.playSound(viewer);
			returnToMenu(viewer, banker);
			return;
		}
		if (!info.canAfford()) {
			viewer.sendMessage(messages.renameCannotAfford(info.fee()));
			SOUND_DENY.playSound(viewer);
			returnToMenu(viewer, banker);
			return;
		}

		new AnvilGUI.Builder()
				.plugin(plugin)
				.title("Rename (Fee: $" + (long) info.fee() + ")")
				.itemLeft(material(XMaterial.NAME_TAG, Material.NAME_TAG))
				.text(info.currentName() == null ? "" : info.currentName())
				.onClick((slot, state) -> {
					if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

					String text = state.getText() == null ? "" : state.getText().trim();

					BankerEconomyContract.Result result = economy.tryRenameAccount(viewer, text);
					String msg = switch (result) {
						case SUCCESS -> {
							SOUND_CONFIRM.playSound(viewer);
							yield messages.renameSuccess(info.currentName(), text);
						}
						case NAME_EMPTY -> messages.renameNameEmpty();
						case NAME_UNCHANGED -> messages.renameNameUnchanged();
						case NO_ACCOUNT -> messages.noAccount();
						case CANNOT_AFFORD_RENAME -> messages.renameCannotAfford(info.fee());
						default -> null;
					};
					if (msg != null) viewer.sendMessage(msg);
					if (result != BankerEconomyContract.Result.SUCCESS
					    && result != BankerEconomyContract.Result.NAME_EMPTY
					    && result != BankerEconomyContract.Result.NAME_UNCHANGED) {
						SOUND_DENY.playSound(viewer);
					}

					// Stay open on NAME_EMPTY / NAME_UNCHANGED so the player can correct the entry without re-opening.
					if (result == BankerEconomyContract.Result.NAME_EMPTY
					    || result == BankerEconomyContract.Result.NAME_UNCHANGED) {
						return Collections.emptyList();
					}

					return List.of(AnvilGUI.ResponseAction.close());
				})
				.onClose(state -> returnToMenu(viewer, banker))
				.open(viewer);
	}

	private void returnToMenu(Player viewer, BankerNpc banker) {
		if (menuView == null) return;
		Bukkit.getScheduler().runTask(plugin, () -> menuView.open(viewer, banker));
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

}
