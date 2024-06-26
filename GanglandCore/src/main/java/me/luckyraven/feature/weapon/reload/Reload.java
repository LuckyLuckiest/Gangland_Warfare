package me.luckyraven.feature.weapon.reload;

import me.luckyraven.exception.PluginException;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.ammo.Ammunition;

import java.util.function.Consumer;

public abstract class Reload implements Cloneable {

	private final Weapon     weapon;
	private final Ammunition ammunition;

	public Reload(Weapon weapon, Ammunition ammunition) {
		this.weapon     = weapon;
		this.ammunition = ammunition;
	}

	public abstract Consumer<Ammunition> consumeAmmunition();

	public abstract Consumer<Weapon> executeReload();

	public void reload() {
		// start reloading sound

		// start the reload with the stored sound
		executeReload().accept(weapon);

		// end reloading sound
	}

	@Override
	public Reload clone() {
		try {
			return (Reload) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}
}
