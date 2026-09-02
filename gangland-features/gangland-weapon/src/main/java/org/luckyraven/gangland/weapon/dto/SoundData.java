package org.luckyraven.gangland.weapon.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.exception.PluginException;

@Data
@NoArgsConstructor
public class SoundData implements Cloneable {

	// Shot sounds
	private SoundEffect shotDefault;
	private SoundEffect shotCustom;

	// Empty mag sounds
	private SoundEffect emptyMagDefault;
	private SoundEffect emptyMagCustom;

	// Reload sounds
	private SoundEffect reloadDefaultBefore;
	private SoundEffect reloadDefaultAfter;
	private SoundEffect reloadCustomStart;
	private SoundEffect reloadCustomMid;
	private SoundEffect reloadCustomEnd;

	// Scope sounds
	private SoundEffect scopeDefault;
	private SoundEffect scopeCustom;

	// Flyby sounds (bullet passing near a player)
	private SoundEffect flybyDefault;
	private SoundEffect flybyCustom;
	private double             flybyRange;

	// Impact sounds (bullet striking a target)
	private SoundEffect impactDefault;
	private SoundEffect impactCustom;

	@Override
	public SoundData clone() {
		try {
			// SoundEffect is an immutable record — the field-reference copy from super.clone() is enough.
			return (SoundData) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
