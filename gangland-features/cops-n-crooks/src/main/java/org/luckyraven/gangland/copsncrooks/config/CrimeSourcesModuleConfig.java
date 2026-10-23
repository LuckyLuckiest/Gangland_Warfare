package org.luckyraven.gangland.copsncrooks.config;

import org.luckyraven.gangland.copsncrooks.wanted.trespass.TrespassTracker;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/** 0.20.0 lane crime-sources: produces the crime-source beans (the trespass tracker so far). */
@Configuration
public class CrimeSourcesModuleConfig {

	@Bean
	public TrespassTracker trespassTracker() {
		return new TrespassTracker();
	}
}
