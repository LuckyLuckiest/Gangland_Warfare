package me.luckyraven.weapon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.luckyraven.exception.PluginException;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BiologicalData implements Cloneable {

	private int          chargeTimePerLevel;
	private int          maxChargeLevel;
	private List<String> effectsPerLevel;
	private double       areaRadius;
	private String       ammoType;

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
