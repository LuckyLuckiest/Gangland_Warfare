package me.luckyraven.feature.weapon.reload;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.feature.weapon.reload.type.InstantReload;
import me.luckyraven.feature.weapon.reload.type.NumberedReload;

@Getter
public enum ReloadType {

	INSTANT,
	ONE,
	NUM;

	@Setter private int amount;

	public static ReloadType getType(String type) {
		return switch (type.toLowerCase()) {
			default -> INSTANT;
			case "one" -> ONE;
			case "num" -> NUM;
		};
	}

	public Reload createInstance(Weapon weapon, Ammunition ammunition) {
		return switch (weapon.getReloadType()) {
			case INSTANT -> new InstantReload(weapon, ammunition);
			case ONE, NUM -> new NumberedReload(weapon, ammunition, amount);
		};
	}

}
