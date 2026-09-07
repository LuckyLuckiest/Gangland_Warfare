package org.luckyraven.gangland.weapon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.luckyraven.keystone.exception.PluginException;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BiologicalData implements Cloneable {

	private int          chargeTimePerLevel;
	private int          maxChargeLevel;
	private List<String> effectsPerLevel;
	/**
	 * Maximum raytrace distance for the released shot, in blocks.
	 */
	private double       range;
	/**
	 * Damage dealt at charge level 1. Each additional level multiplies this value linearly (level N →
	 * {@code N * baseDamage}).
	 */
	private double       baseDamage;

	@Override
	public BiologicalData clone() {
		try {
			BiologicalData clone = (BiologicalData) super.clone();
			clone.effectsPerLevel = new ArrayList<>(effectsPerLevel);
			return clone;
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
