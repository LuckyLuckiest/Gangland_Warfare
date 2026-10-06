package org.luckyraven.gangland.copsncrooks.config;

import lombok.CustomLog;
import org.luckyraven.gangland.copsncrooks.integration.config.GanglandCopSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopSettings;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.persistence.FileManager;

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

	/**
	 * The cop knobs of {@code copsncrooks/cops.yml} (0.15.1, formerly settings.yml). Registered only: the next
	 * {@code initializeAll()} (at the latest {@code copLoader}'s, which depends on this bean) parses it before the
	 * cop config is built, and a reload re-runs it in the same order. {@code settings} is an ordering edge: the core
	 * settings file is loaded before the legacy fallback reads it.
	 */
	@Bean
	public CopSettings copSettings(FileManager fileManager, Settings settings) {
		GanglandCopSettings copSettings = new GanglandCopSettings(fileManager);
		fileManager.registerInitializer(copSettings);
		return copSettings;
	}
}
