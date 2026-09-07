package org.luckyraven.gangland.weapon.events.projectile;

import lombok.Getter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.events.WeaponEvent;

@Getter
public class WeaponShootEvent extends WeaponEvent implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	private final LivingEntity shooter;

	private boolean cancelled;

	public WeaponShootEvent(Weapon weapon, LivingEntity shooter) {
		super(weapon);

		this.shooter = shooter;
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

	@NotNull
	public HandlerList getHandlers() {
		return handler;
	}

}
