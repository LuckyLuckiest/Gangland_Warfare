package me.luckyraven.weapon.reload;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.reload.type.InstantReload;
import me.luckyraven.weapon.reload.type.NumberedReload;

@Getter
public enum ReloadType {

	INSTANT,
	ONE,
	NUM;

	@Setter
	private int amount;

	public static ReloadType getType(String type) {
		return switch (type.toLowerCase()) {
			case "one" -> ONE;
			case "num" -> NUM;
			default -> INSTANT;
		};
	}

	public Reload createInstance(Weapon weapon, Ammunition ammunition) {
		return switch (weapon.getReloadType()) {
			case INSTANT -> new InstantReload(weapon, ammunition);
			case ONE, NUM -> new NumberedReload(weapon, ammunition, amount);
		};
	}

}
