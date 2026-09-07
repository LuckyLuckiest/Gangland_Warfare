package org.luckyraven.gangland.copsncrooks.config;

import lombok.CustomLog;
import org.luckyraven.gangland.copsncrooks.integration.config.GanglandCivilianSettings;
import org.luckyraven.gangland.copsncrooks.integration.config.GanglandCivilianSpawnConfigProvider;
import org.luckyraven.gangland.copsncrooks.integration.config.GanglandCopSettings;
import org.luckyraven.gangland.copsncrooks.npc.civilian.config.CivilianSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopSettings;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;

/**
 * FILE-phase wiring for the cops-n-crooks module. These three beans used to live in the core's {@code FileConfig}
 * (pure data, no file load, so they naturally fit FILE); they moved here verbatim when the feature became a
 * runtime module.
 */
@CustomLog
@Configuration(phase = Phase.FILE)
public class CopsNCrooksFileConfig {

	@Bean
	public CopSettings copSettings() {
		return new GanglandCopSettings();
	}

	@Bean
	public CivilianSettings civilianSettings() {
		return new GanglandCivilianSettings();
	}

	@Bean
	public GanglandCivilianSpawnConfigProvider civilianSpawnConfigProvider() {
		return new GanglandCivilianSpawnConfigProvider();
	}
}
