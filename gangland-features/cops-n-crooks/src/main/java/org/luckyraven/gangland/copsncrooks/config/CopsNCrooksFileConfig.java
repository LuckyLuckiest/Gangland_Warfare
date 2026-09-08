package org.luckyraven.gangland.copsncrooks.config;

import lombok.CustomLog;
import org.luckyraven.gangland.copsncrooks.integration.config.GanglandCopSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopSettings;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;

/**
 * FILE-phase wiring for the cops-n-crooks module. This bean used to live in the core's {@code FileConfig}
 * (pure data, no file load, so it naturally fits FILE); it moved here verbatim when the feature became a
 * runtime module. The two civilian-only beans this class used to also carry
 * ({@code civilianSettings()}/{@code civilianSpawnConfigProvider()}) moved to {@code CiviliansFileConfig} in
 * {@code gangland-civilians} (T-H4) — review M3 removed the stale duplicates left here.
 */
@CustomLog
@Configuration(phase = Phase.FILE)
public class CopsNCrooksFileConfig {

	@Bean
	public CopSettings copSettings() {
		return new GanglandCopSettings();
	}
}
