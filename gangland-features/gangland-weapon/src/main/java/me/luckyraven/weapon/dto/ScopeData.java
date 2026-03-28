package me.luckyraven.weapon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.luckyraven.exception.PluginException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScopeData implements Cloneable {

	private int     level;
	private boolean scoped;

	@Override
	public ScopeData clone() {
		try {
			return (ScopeData) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
