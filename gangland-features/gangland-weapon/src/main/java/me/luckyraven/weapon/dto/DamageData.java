package me.luckyraven.weapon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.luckyraven.exception.PluginException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DamageData implements Cloneable {

	private double explosionDamage;
	private int    fireTicks;
	private double headDamage;
	private int    criticalHitChance;
	private double criticalHitDamage;

	@Override
	public DamageData clone() {
		try {
			return (DamageData) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
