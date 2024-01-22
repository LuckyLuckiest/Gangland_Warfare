package me.luckyraven.feature.weapon;

public enum WeaponTag {

	/**
	 * The name of the weapon. Usually when trying to initialize the held weapon.
	 */
	WEAPON,

	/**
	 * The current selective fire state. The state is unknown when the weapon is held, thus this data is saved.
	 * <p>
	 * 0 = auto, 1 = burst, 2 = single.
	 */
	SELECTIVE_FIRE,

	/**
	 * The current amount of ammo left in the weapon. This can be ambiguous after time.
	 */
	AMMO_LEFT

}
