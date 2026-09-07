package org.luckyraven.gangland.weapon.events.selective;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.events.WeaponEvent;

public class WeaponChangeSelectiveFireEvent extends WeaponEvent implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	private boolean cancelled;

	public WeaponChangeSelectiveFireEvent(Weapon weapon) {
		super(weapon);
	}

	private static HandlerList getHandlerList() {
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
	public @NotNull HandlerList getHandlers() {
		return handler;
	}

}
