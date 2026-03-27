package me.luckyraven.gadget.repair;

import lombok.Getter;
import me.luckyraven.gadget.repair.config.RepairConfig;
import me.luckyraven.gadget.repair.events.RepairCompleteEvent;
import me.luckyraven.gadget.repair.events.RepairStartEvent;
import me.luckyraven.gadget.repair.material.RepairMaterial;
import me.luckyraven.gadget.repair.material.RepairMaterialManager;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.repair.Repairable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public class RepairManager {

	private final RepairMaterialManager materialManager;

	@Nullable
	private RepairMessages messages;

	public RepairManager() {
		this.materialManager = new RepairMaterialManager();
	}

	public void setMessages(@NotNull RepairMessages messages) {
		this.messages = messages;
	}

	public void load(@NotNull RepairConfig config) {
		materialManager.load(config);
	}

	/**
	 * Applies a repair to a repairable item using the given material.
	 *
	 * @param player the player performing the repair
	 * @param repairable the item being repaired
	 * @param repairMaterial the material being consumed
	 *
	 * @return true if the repair was applied successfully
	 */
	public boolean applyRepair(@NotNull Player player, @NotNull Repairable repairable,
	                           @NotNull RepairMaterial repairMaterial) {
		if (repairable.isFullyRepaired()) {
			if (messages != null) player.sendMessage(messages.getAlreadyFullyRepaired());
			return false;
		}

		if (!repairMaterial.isCompatibleWith(repairable.getRepairableType())) {
			if (messages != null) player.sendMessage(messages.getIncompatibleMaterial());
			return false;
		}

		RepairStartEvent startEvent = new RepairStartEvent(player, repairable, repairMaterial);
		Bukkit.getPluginManager().callEvent(startEvent);

		if (startEvent.isCancelled()) {
			if (messages != null) player.sendMessage(messages.getRepairCancelled());
			return false;
		}

		int current    = repairable.getCurrentRepairDurability();
		int max        = repairable.getMaxRepairDurability();
		int restored   = calculateRestore(max, repairMaterial);
		int newValue   = Math.min(max, current + restored);
		int actualGain = newValue - current;

		repairable.setCurrentRepairDurability(newValue);

		Bukkit.getPluginManager().callEvent(new RepairCompleteEvent(player, repairable, repairMaterial, actualGain));

		SoundConfiguration.playSounds(player, repairMaterial.getData().getRepairDefaultSound(),
		                              repairMaterial.getData().getRepairCustomSound());

		if (messages != null) {
			player.sendMessage(messages.getRepairComplete(actualGain, newValue, max));
		}

		return true;
	}

	private int calculateRestore(int maxDurability, @NotNull RepairMaterial repairMaterial) {
		var data        = repairMaterial.getData();
		int fromFlat    = data.getRestoreAmount();
		int fromPercent = (int) (maxDurability * data.getRestorePercent() / 100.0);
		return Math.max(fromFlat, fromPercent);
	}
}
