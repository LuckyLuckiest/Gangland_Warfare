package org.luckyraven.gangland.weapon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.luckyraven.gangland.exception.PluginException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DurabilityData implements Cloneable {

	private short onShot;
	private int   consumeOnTime;

	@Override
	public DurabilityData clone() {
		try {
			return (DurabilityData) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
