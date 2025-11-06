package me.luckyraven.weapon.events;

import lombok.Getter;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.projectile.WeaponProjectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class WeaponShootEvent extends WeaponEvent implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	private final WeaponProjectile<?> weaponProjectile;

	private boolean cancelled;

	public WeaponShootEvent(Weapon weapon, WeaponProjectile<?> weaponProjectile) {
		super(weapon);

		this.weaponProjectile = weaponProjectile;
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
