package org.luckyraven.gangland.config;

import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.logging.DebugLoggingInitializer;

/**
 * Bean wiring for {@link DebugLoggingInitializer}. The {@code Settings} parameter is unused in the body; its only
 * purpose is to establish a dependency edge so the bean graph topologically sorts this bean after the settings file has
 * finished loading.
 *
 * <p>The initializer scans Gangland's own base package through Gangland's classloader — under the Keystone
 * dependency-plugin model, Keystone's loader cannot see this plugin's {@code module.properties} resources, so the
 * empty-{@code Debug.Modules} auto-discovery must run against ours (Keystone 1.7.1, Phase H3).
 */
@Configuration(phase = Phase.FILE)
public class DebugLoggingConfig {

	@Bean
	public DebugLoggingInitializer debugLoggingInitializer(Settings settings) {
		DebugLoggingInitializer initializer =
				new DebugLoggingInitializer("org.luckyraven.gangland", Gangland.class.getClassLoader());

		if (Settings.isDebugEnabled()) {
			initializer.initialize(Settings.getDebugModules());
		}

		return initializer;
	}

}
