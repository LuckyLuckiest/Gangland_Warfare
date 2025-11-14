package me.luckyraven.weapon.events;

import lombok.Getter;
import me.luckyraven.weapon.Weapon;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class WeaponKillEntityEvent extends WeaponEvent implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	private final Entity killer;
	private final Entity killed;

	private boolean cancelled;

	public WeaponKillEntityEvent(Weapon weapon, Entity killer, Entity killed) {
		super(weapon);

		this.killer = killer;
		this.killed = killed;
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	@Override
	@NotNull
	public HandlerList getHandlers() {
		return handler;
	}

}
