package org.luckyraven.gangland.file.configuration.gang;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.contract.GangSettingsContract;

/**
 * Routes {@link GangSettingsContract} calls from the gang module through the static {@link Settings} reader in
 * gangland-impl, keeping the gang module free of a direct Settings import.
 *
 * <p>Split down to these 3 gang-display getters (WS5 G0, B2) — see {@code GanglandIdentitySettings} for the
 * identity-slice getters that moved off this class.
 */
public final class GanglandGangSettings implements GangSettingsContract {

	@Override
	public String getGangDisplayNameChar() {
		return Settings.getGangDisplayNameChar();
	}

	@Override
	public String getGangRankHead() {
		return Settings.getGangRankHead();
	}

	@Override
	public String getGangRankTail() {
		return Settings.getGangRankTail();
	}
}
