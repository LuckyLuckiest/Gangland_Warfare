package me.luckyraven.file.configuration.copsncrooks;

import me.luckyraven.copsncrooks.npc.civilian.config.CivilianSettings;
import me.luckyraven.file.configuration.Settings;

/**
 * Implements {@link CivilianSettings} by delegating to {@link Settings} static getters.
 * <p>
 * This class lives in {@code gangland-impl} so that the {@code cops-n-crooks} feature module never imports
 * {@link Settings} directly.
 */
public class GanglandCivilianSettings implements CivilianSettings {

	@Override
	public boolean isCivilianAiEnabled() {
		return Settings.isCivilianAiEnabled();
	}

	@Override
	public int getCivilianAiTickRate() {
		return Settings.getCivilianAiTickRate();
	}

	// ── NpcNavigationConfig ───────────────────────────────────────────────────

	@Override
	public int getAiTickRate() {
		return Settings.getCivilianAiTickRate();
	}

	@Override
	public int getNavigationRecalculationTicks() {
		return Settings.getNpcNavRecalculationTicks();
	}

	@Override
	public int getStuckCheckIntervalTicks() {
		return Settings.getNpcNavStuckCheckInterval();
	}

	@Override
	public int getMaxStuckChecks() {
		return Settings.getNpcNavMaxStuckChecks();
	}

	@Override
	public int getMaxHopelessStuckChecks() {
		return Settings.getNpcNavMaxHopelessStuckChecks();
	}

	@Override
	public double getHopelessCloseThreshold() {
		return Settings.getNpcNavHopelessCloseThreshold();
	}

	@Override
	public double getMinProgressDistance() {
		return Settings.getNpcNavMinProgressDistance();
	}

	@Override
	public double getRangedMinDistance() {
		return Settings.getNpcNavRangedMinDistance();
	}

	@Override
	public double getRangedMaxDistance() {
		return Settings.getNpcNavRangedMaxDistance();
	}

	@Override
	public int getMinRepathAfterLossTicks() {
		return Settings.getNpcNavMinRepathAfterLossTicks();
	}
}
