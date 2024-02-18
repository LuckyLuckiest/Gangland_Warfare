package me.luckyraven.feature.weapon;

import lombok.Getter;

@Getter
public enum WeaponTag {

	/**
	 * The UUID of the weapon.
	 */
	UUID(VariableType.STATIC),

	/**
	 * The name of the weapon. Usually when trying to initialize the held weapon.
	 */
	WEAPON(VariableType.STATIC),

	/**
	 * The current selective fire state. The state is unknown when the weapon is held, thus this data is saved.
	 * <p>
	 * 0 = auto, 1 = burst, 2 = single.
	 */
	SELECTIVE_FIRE(VariableType.DYNAMIC),

	/**
	 * The current amount of ammo left in the weapon. This can be ambiguous after time.
	 */
	AMMO_LEFT(VariableType.DYNAMIC);

	private final VariableType variableType;

	WeaponTag(VariableType variableType) {
		this.variableType = variableType;
	}

	public enum VariableType {
		STATIC,
		DYNAMIC
	}

}
