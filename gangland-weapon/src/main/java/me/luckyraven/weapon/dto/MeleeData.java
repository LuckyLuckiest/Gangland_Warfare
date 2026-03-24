package me.luckyraven.weapon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.luckyraven.exception.PluginException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeleeData implements Cloneable {

	private double damage;
	private double range;
	private int    cooldown;
	private double knockback;

	@Override
	public MeleeData clone() {
		try {
			return (MeleeData) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
