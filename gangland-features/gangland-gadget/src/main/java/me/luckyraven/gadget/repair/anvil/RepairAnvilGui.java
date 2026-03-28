package me.luckyraven.gadget.repair.anvil;

import me.luckyraven.gadget.repair.RepairManager;
import me.luckyraven.gadget.repair.RepairMessages;
import me.luckyraven.gadget.repair.material.RepairMaterial;
import me.luckyraven.gadget.repair.material.RepairMaterialManager;
import me.luckyraven.item.repair.Repairable;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class RepairAnvilGui {

	private static final int OUTPUT_SLOT = 2;

	private final JavaPlugin    plugin;
	private final RepairManager repairManager;

	public RepairAnvilGui(@NotNull JavaPlugin plugin, @NotNull RepairManager repairManager) {
		this.plugin        = plugin;
		this.repairManager = repairManager;
	}

	/**
	 * Opens the repair GUI for the given player and repairable item.
	 *
	 * @param player the player opening the GUI
	 * @param repairable the item to repair
	 * @param weaponItem the ItemStack representation of the repairable
	 */
	public void open(@NotNull Player player, @NotNull Repairable repairable, @NotNull ItemStack weaponItem) {
		RepairMaterialManager materialManager = repairManager.getMaterialManager();
		RepairMessages        messages        = repairManager.getMessages();

		ItemStack materialItem = findMaterialInInventory(player);

		String text = materialItem != null ? RepairMaterial.getMaterialId(materialItem) : "";

		new AnvilGUI.Builder().plugin(plugin)
		                      .title("Repair")
		                      .itemLeft(weaponItem)
		                      .text(text != null ? text : "")
		                      .onClick((slot, stateSnapshot) -> {
								  if (slot != OUTPUT_SLOT) return Collections.emptyList();

								  Player    viewer          = stateSnapshot.getPlayer();
								  ItemStack currentMaterial = findMaterialInInventory(viewer);

								  if (currentMaterial == null) {
									  if (messages != null) viewer.sendMessage(messages.getNoMaterialAvailable());
									  return List.of(AnvilGUI.ResponseAction.close());
								  }

								  RepairMaterial selectedMaterial = materialManager.getMaterial(currentMaterial);
								  if (selectedMaterial == null || !RepairMaterial.hasUsesLeft(currentMaterial)) {
									  if (messages != null) viewer.sendMessage(messages.getNoMaterialAvailable());
									  return List.of(AnvilGUI.ResponseAction.close());
								  }

								  boolean success = repairManager.applyRepair(viewer, repairable, selectedMaterial);

								  if (success) {
									  ItemStack updatedMaterial = RepairMaterial.consumeUse(currentMaterial);
									  updateMaterialInInventory(viewer, currentMaterial, updatedMaterial);
									  updateWeaponInHand(viewer, repairable, weaponItem);
								  }

								  return List.of(AnvilGUI.ResponseAction.close());
							  })
		                      .open(player);
	}

	@Nullable
	private ItemStack findMaterialInInventory(@NotNull Player player) {
		RepairMaterialManager materialManager = repairManager.getMaterialManager();
		for (ItemStack item : player.getInventory().getContents()) {
			if (item == null) continue;
			if (materialManager.getMaterial(item) != null && RepairMaterial.hasUsesLeft(item)) {
				return item;
			}
		}
		return null;
	}

	private void updateMaterialInInventory(@NotNull Player player, @NotNull ItemStack original,
	                                       @Nullable ItemStack updated) {
		if (updated == null) {
			player.getInventory().remove(original);
		} else {
			for (int i = 0; i < player.getInventory().getSize(); i++) {
				ItemStack slot       = player.getInventory().getItem(i);
				String    materialId = RepairMaterial.getMaterialId(slot);
				if (slot != null && materialId != null && materialId.equals(RepairMaterial.getMaterialId(original))) {
					player.getInventory().setItem(i, updated);
					break;
				}
			}
		}
	}

	private void updateWeaponInHand(@NotNull Player player, @NotNull Repairable repairable,
	                                @NotNull ItemStack originalWeaponItem) {
		ItemStack repaired = repairable.buildItem();
		player.getInventory().setItemInMainHand(repaired);
	}
}
