package me.luckyraven.weapon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.luckyraven.exception.PluginException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncendiaryData implements Cloneable {

	private double coneAngle;
	private double range;
	private int    fireDuration;
	private int    tickRate;
	private int    consumeRate;

	@Override
	public IncendiaryData clone() {
		try {
			return (IncendiaryData) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
