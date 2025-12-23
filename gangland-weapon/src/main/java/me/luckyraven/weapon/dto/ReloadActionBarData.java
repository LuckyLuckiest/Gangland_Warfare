package me.luckyraven.weapon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.luckyraven.exception.PluginException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReloadActionBarData implements Cloneable {

	private String reloading;
	private String opening;

	@Override
	public ReloadActionBarData clone() {
		try {
			return (ReloadActionBarData) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
