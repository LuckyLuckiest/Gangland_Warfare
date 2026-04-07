package me.luckyraven.file.configuration.weapon;

import me.luckyraven.file.configuration.Settings;
import me.luckyraven.weapon.modifiers.BlockRegenerationSettings;

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
