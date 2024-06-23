package me.luckyraven.feature.weapon.events;

import lombok.Getter;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.projectile.type.Bullet;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class WeaponShootEvent extends WeaponEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	private final Bullet bullet;

	private boolean cancelled;

	public WeaponShootEvent(Weapon weapon, Bullet bullet) {
		super(weapon);

		this.bullet = bullet;
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
