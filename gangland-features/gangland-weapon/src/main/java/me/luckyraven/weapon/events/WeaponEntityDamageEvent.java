package me.luckyraven.weapon.events;

import lombok.Getter;
import me.luckyraven.weapon.Weapon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a non-projectile weapon (incendiary, biological) hits a non-living entity such as a vehicle. Listeners
 * such as {@code CarDamageListener} intercept this to apply the correct weapon damage.
 */
@Getter
public class WeaponEntityDamageEvent extends WeaponEvent {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Entity entity;
	private final double damage;
	private final Player shooter;

	public WeaponEntityDamageEvent(Weapon weapon, Entity entity, double damage, Player shooter) {
		super(weapon);
		this.entity  = entity;
		this.damage  = damage;
		this.shooter = shooter;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	@NotNull
	public HandlerList getHandlers() {
		return HANDLERS;
	}

}
