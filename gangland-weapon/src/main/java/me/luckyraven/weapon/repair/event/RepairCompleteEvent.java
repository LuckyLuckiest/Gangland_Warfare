package me.luckyraven.weapon.repair.event;

import lombok.Getter;
import me.luckyraven.weapon.repair.Repairable;
import me.luckyraven.weapon.repair.material.RepairMaterial;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class RepairCompleteEvent extends RepairEvent {

	private static final HandlerList handler = new HandlerList();

	private final int restoredDurability;

	public RepairCompleteEvent(@NotNull Player player, @NotNull Repairable repairable,
							   @NotNull RepairMaterial repairMaterial, int restoredDurability) {
		super(player, repairable, repairMaterial);
		this.restoredDurability = restoredDurability;
	}

	public static HandlerList getHandler() {
		return handler;
	}

	@Override
	@NotNull
	public HandlerList getHandlers() {
		return handler;
	}
}
