package me.luckyraven.weapon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.luckyraven.exception.PluginException;
import me.luckyraven.weapon.ammo.Ammunition;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AmmunitionData implements Cloneable {

	private Ammunition ammoType;
	private int        maxMagCapacity;
	private int        consumeRate;
	private int        restore;

	@Override
	public AmmunitionData clone() {
		try {
			return (AmmunitionData) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
