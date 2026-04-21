package me.luckyraven.config;

import me.luckyraven.file.configuration.Settings;
import me.luckyraven.logging.DebugLoggingInitializer;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.util.autowire.bean.Phase;

/**
 * Bean wiring for {@link DebugLoggingInitializer}. The {@code Settings} parameter is unused in the body; its only
 * purpose is to establish a dependency edge so the bean graph topologically sorts this bean after the settings file
 * has finished loading.
 */
@Configuration(phase = Phase.FILE)
public class DebugLoggingConfig {

	@Bean
	public DebugLoggingInitializer debugLoggingInitializer(Settings settings) {
		DebugLoggingInitializer initializer = new DebugLoggingInitializer();
		initializer.initialize();
		return initializer;
	}

}
