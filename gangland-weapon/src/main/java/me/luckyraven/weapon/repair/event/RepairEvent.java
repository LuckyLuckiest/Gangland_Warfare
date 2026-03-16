package me.luckyraven.weapon.repair.event;

import lombok.Getter;
import me.luckyraven.weapon.repair.Repairable;
import me.luckyraven.weapon.repair.material.RepairMaterial;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

@Getter
public abstract class RepairEvent extends Event {

	private final Player         player;
	private final Repairable     repairable;
	private final RepairMaterial repairMaterial;

	protected RepairEvent(@NotNull Player player, @NotNull Repairable repairable,
						  @NotNull RepairMaterial repairMaterial) {
		this.player         = player;
		this.repairable     = repairable;
		this.repairMaterial = repairMaterial;
	}
}
