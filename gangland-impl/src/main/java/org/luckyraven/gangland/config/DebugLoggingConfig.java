package org.luckyraven.gangland.config;

import org.luckyraven.gangland.core.bean.Bean;
import org.luckyraven.gangland.core.bean.Configuration;
import org.luckyraven.gangland.core.bean.Phase;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.logging.DebugLoggingInitializer;

/**
 * Bean wiring for {@link DebugLoggingInitializer}. The {@code Settings} parameter is unused in the body; its only
 * purpose is to establish a dependency edge so the bean graph topologically sorts this bean after the settings file has
 * finished loading.
 */
@Configuration(phase = Phase.FILE)
public class DebugLoggingConfig {

	@Bean
	public DebugLoggingInitializer debugLoggingInitializer(Settings settings) {
		DebugLoggingInitializer initializer = new DebugLoggingInitializer();

		if (Settings.isDebugEnabled()) {
			initializer.initialize(Settings.getDebugModules());
		}

		return initializer;
	}

}
