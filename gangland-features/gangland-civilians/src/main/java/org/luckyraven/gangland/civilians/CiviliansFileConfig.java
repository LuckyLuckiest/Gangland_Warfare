package org.luckyraven.gangland.civilians;

import lombok.CustomLog;
import org.luckyraven.gangland.civilians.integration.GanglandCivilianSettings;
import org.luckyraven.gangland.civilians.integration.GanglandCivilianSpawnConfigProvider;
import org.luckyraven.gangland.civilians.npc.config.CivilianSettings;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;

/**
 * FILE-phase wiring for the civilians module. These two beans used to live in cops-n-crooks's own
 * {@code CopsNCrooksFileConfig} (pure data, no file load, so they naturally fit FILE); they moved here verbatim
 * when civilians became its own runtime module (T-H2/T-H4).
 */
@CustomLog
@Configuration(phase = Phase.FILE)
public class CiviliansFileConfig {

	@Bean
	public CivilianSettings civilianSettings() {
		return new GanglandCivilianSettings();
	}

	@Bean
	public GanglandCivilianSpawnConfigProvider civilianSpawnConfigProvider() {
		return new GanglandCivilianSpawnConfigProvider();
	}
}
