package me.luckyraven.weapon.repair;

import lombok.Getter;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.weapon.events.repair.RepairCompleteEvent;
import me.luckyraven.weapon.events.repair.RepairStartEvent;
import me.luckyraven.weapon.repair.config.RepairConfig;
import me.luckyraven.weapon.repair.material.RepairMaterial;
import me.luckyraven.weapon.repair.material.RepairMaterialManager;
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
			if (messages != null) player.sendMessage(messages.alreadyFullyRepaired());
			return false;
		}

		if (!repairMaterial.isCompatibleWith(repairable.getRepairableType())) {
			if (messages != null) player.sendMessage(messages.incompatibleMaterial());
			return false;
		}

		RepairStartEvent startEvent = new RepairStartEvent(player, repairable, repairMaterial);
		Bukkit.getPluginManager().callEvent(startEvent);

		if (startEvent.isCancelled()) {
			if (messages != null) player.sendMessage(messages.repairCancelled());
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
			player.sendMessage(messages.repairComplete(actualGain, newValue, max));
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
