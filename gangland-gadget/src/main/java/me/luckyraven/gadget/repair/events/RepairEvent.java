package me.luckyraven.gadget.repair.events;

import lombok.Getter;
import me.luckyraven.gadget.repair.material.RepairMaterial;
import me.luckyraven.util.repair.Repairable;
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
