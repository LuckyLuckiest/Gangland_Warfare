package org.luckyraven.gangland.file.configuration.weapon;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.weapon.modifiers.BlockRegenerationSettings;

/**
 * {@link BlockRegenerationSettings} implementation backed by {@link Settings}.
 */
public class GanglandBlockRegenerationSettings implements BlockRegenerationSettings {

	@Override
	public int getRestoreDelayTicks() {
		return Settings.getBlockRestoreDelayTicks();
	}

	@Override
	public int getRegenerationDelayTicks() {
		return Settings.getBlockRegenerationDelayTicks();
	}

	@Override
	public int getRegenerationStepTicks() {
		return Settings.getBlockRegenerationStepTicks();
	}
}
