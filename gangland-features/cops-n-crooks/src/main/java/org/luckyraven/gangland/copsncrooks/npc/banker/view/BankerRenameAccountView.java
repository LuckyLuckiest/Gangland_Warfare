package org.luckyraven.gangland.copsncrooks.npc.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.banker.economy.BankerEconomyContract;
import org.luckyraven.gangland.copsncrooks.npc.banker.economy.BankerEconomyContract.RenameInfo;
import org.luckyraven.gangland.copsncrooks.npc.banker.message.BankerMessageContract;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.inventory.flow.Panel;

import java.util.Collections;
import java.util.List;

/**
 * Anvil prompt for renaming the bank account. Not a {@link Panel} itself — it has no inventory of its own; the whole
 * interaction is one AnvilGUI popup. Invoked from {@link BankerMenuView}'s RENAME click with the active flow host so
 * the flow can be suspended for the anvil detour and resumed (returning to the menu panel) on anvil close.
 */
@RequiredArgsConstructor
public final class BankerRenameAccountView {

	private static final SoundEffect SOUND_CONFIRM = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                               "ENTITY_PLAYER_LEVELUP", 1.0f, 1.2f);
	private static final SoundEffect SOUND_DENY    = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                               "ENTITY_VILLAGER_NO", 0.8f, 1.0f);

	private final JavaPlugin            plugin;
	private final BankerEconomyContract economy;
	private final BankerMessageContract messages;

	public void open(MultiPanelInventory<BankerFlowSession> host, Player viewer) {
		RenameInfo info = economy.renameInfo(viewer);
		if (!info.hasAccount()) {
			viewer.sendMessage(messages.noAccount());
			playSoundNextTick(viewer, SOUND_DENY);
			return;
		}
		if (!info.canAfford()) {
			viewer.sendMessage(messages.renameCannotAfford(info.fee()));
			playSoundNextTick(viewer, SOUND_DENY);
			return;
		}

		host.suspend();
		new AnvilGUI.Builder()
				.plugin(plugin)
				.title("Rename (Fee: $" + info.fee().toPlainString() + ")")
				.itemLeft(material(XMaterial.NAME_TAG, Material.NAME_TAG))
				.text(info.currentName() == null ? "" : info.currentName())
				.onClick((slot, state) -> {
					if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

					String text = state.getText() == null ? "" : state.getText().trim();

					BankerEconomyContract.Result result = economy.tryRenameAccount(viewer, text);
					String msg = switch (result) {
						case SUCCESS -> {
							playSoundNextTick(viewer, SOUND_CONFIRM);
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
						playSoundNextTick(viewer, SOUND_DENY);
					}

					// Stay open on NAME_EMPTY / NAME_UNCHANGED so the player can correct the entry without re-opening.
					if (result == BankerEconomyContract.Result.NAME_EMPTY
					    || result == BankerEconomyContract.Result.NAME_UNCHANGED) {
						return Collections.emptyList();
					}

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

	private void playSoundNextTick(Player player, SoundEffect sound) {
		Bukkit.getScheduler().runTask(plugin, () -> sound.playSound(player));
	}

}
