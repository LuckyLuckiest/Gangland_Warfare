package me.luckyraven.feature.weapon.reload.type;

import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.feature.weapon.reload.Reload;

import java.util.function.Consumer;

public class NumberedReload extends Reload {

	private final int amount;

	public NumberedReload(Weapon weapon, Ammunition ammunition, int amount) {
		super(weapon, ammunition);

		this.amount = amount;
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
