package me.luckyraven.feature.weapon.events;

import lombok.Getter;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.projectile.WeaponProjectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class WeaponShootEvent extends WeaponEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	private final WeaponProjectile<?> weaponProjectile;

	private boolean cancelled;

	public WeaponShootEvent(Weapon weapon, WeaponProjectile<?> weaponProjectile) {
		super(weapon);

		this.weaponProjectile = weaponProjectile;
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
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}
}
