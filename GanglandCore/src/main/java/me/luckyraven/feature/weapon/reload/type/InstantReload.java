package me.luckyraven.feature.weapon.reload.type;

import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.feature.weapon.reload.Reload;

import java.util.function.Consumer;

public class InstantReload extends Reload {

	public InstantReload(Weapon weapon, Ammunition ammunition) {
		super(weapon, ammunition);
	}

	@Override
	public Consumer<Ammunition> consumeAmmunition() {
		return ammunition -> {

		};
	}

	@Override
	public Consumer<Weapon> executeReload() {
		return weapon -> {

		};
	}
}
