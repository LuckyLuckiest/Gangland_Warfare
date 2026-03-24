package me.luckyraven.weapon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.luckyraven.exception.PluginException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThrowableData implements Cloneable {

	private int     fuseTime;
	private double  explosionRadius;
	private int     explosionDamage;
	private int     fireTicks;
	private boolean bounces;
	private String  entityType;

	@Override
	public ThrowableData clone() {
		try {
			return (ThrowableData) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
