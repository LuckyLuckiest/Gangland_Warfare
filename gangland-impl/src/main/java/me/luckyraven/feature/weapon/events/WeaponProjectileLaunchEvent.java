package me.luckyraven.feature.weapon.events;

import lombok.Getter;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.projectile.WeaponProjectile;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class WeaponProjectileLaunchEvent extends WeaponEvent implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	private final Projectile          projectile;
	private final WeaponProjectile<?> weaponProjectile;

	private boolean cancelled;

	public WeaponProjectileLaunchEvent(Weapon weapon, Projectile projectile, WeaponProjectile<?> weaponProjectile) {
		super(weapon);

		this.weaponProjectile = weaponProjectile;
		this.projectile       = projectile;
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
	@Override
	public HandlerList getHandlers() {
		return handler;
	}

}
