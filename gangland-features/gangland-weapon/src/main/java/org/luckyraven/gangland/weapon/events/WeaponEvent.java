package org.luckyraven.gangland.weapon.events;

import lombok.Getter;
import org.bukkit.event.Event;
import org.luckyraven.gangland.weapon.Weapon;

@Getter
public abstract class WeaponEvent extends Event {

	private final Weapon weapon;

	public WeaponEvent(Weapon weapon) {
		this.weapon = weapon;
	}

}
