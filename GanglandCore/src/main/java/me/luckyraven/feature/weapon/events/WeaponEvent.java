package me.luckyraven.feature.weapon.events;

import lombok.Getter;
import me.luckyraven.feature.weapon.Weapon;
import org.bukkit.event.Event;

@Getter
public abstract class WeaponEvent extends Event {

	private final Weapon weapon;

	public WeaponEvent(Weapon weapon) {
		this.weapon = weapon;
	}
}
