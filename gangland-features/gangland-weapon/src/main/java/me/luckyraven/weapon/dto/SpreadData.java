package me.luckyraven.weapon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.luckyraven.exception.PluginException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpreadData implements Cloneable {

	private double  start;
	private int     resetTime;
	private double  changeBase;
	private boolean resetOnBound;
	private double  boundMinimum;
	private double  boundMaximum;

	@Override
	public SpreadData clone() {
		try {
			return (SpreadData) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
