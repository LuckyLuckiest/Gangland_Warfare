package me.luckyraven.weapon.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import me.luckyraven.exception.PluginException;
import me.luckyraven.util.configuration.SoundConfiguration;

@Data
@NoArgsConstructor
public class SoundData implements Cloneable {

	// Shot sounds
	private SoundConfiguration shotDefault;
	private SoundConfiguration shotCustom;

	// Empty mag sounds
	private SoundConfiguration emptyMagDefault;
	private SoundConfiguration emptyMagCustom;

	// Reload sounds
	private SoundConfiguration reloadDefaultBefore;
	private SoundConfiguration reloadDefaultAfter;
	private SoundConfiguration reloadCustomStart;
	private SoundConfiguration reloadCustomMid;
	private SoundConfiguration reloadCustomEnd;

	// Scope sounds
	private SoundConfiguration scopeDefault;
	private SoundConfiguration scopeCustom;

	@Override
	public SoundData clone() {
		SoundData copy;

		try {
			copy = (SoundData) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}

		copy.shotDefault         = cloneSound(shotDefault);
		copy.shotCustom          = cloneSound(shotCustom);
		copy.emptyMagDefault     = cloneSound(emptyMagDefault);
		copy.emptyMagCustom      = cloneSound(emptyMagCustom);
		copy.reloadDefaultBefore = cloneSound(reloadDefaultBefore);
		copy.reloadDefaultAfter  = cloneSound(reloadDefaultAfter);
		copy.reloadCustomStart   = cloneSound(reloadCustomStart);
		copy.reloadCustomMid     = cloneSound(reloadCustomMid);
		copy.reloadCustomEnd     = cloneSound(reloadCustomEnd);
		copy.scopeDefault        = cloneSound(scopeDefault);
		copy.scopeCustom         = cloneSound(scopeCustom);

		return copy;
	}

	private SoundConfiguration cloneSound(SoundConfiguration sound) {
		return sound != null ? sound.clone() : null;
	}

}
