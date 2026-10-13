package org.luckyraven.gangland.copsncrooks.config;

import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.SightSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.SightModel;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

import java.util.function.Supplier;

/** 0.20.0 lane sight: the {@link SightModel} bean, registered as a range factor on {@link CopPerception}. */
@Configuration
public class SightModuleConfig {

	@Bean
	public SightModel sightModel(CopPerception perception, CopLoader copLoader, CivilianNpcRegistry civilians,
	                             CopManager copManager) {
		// Read on every call so /glw reload applies without rebuilding the bean.
		Supplier<SightSettings> live = () -> {
			CopConfigProvider provider = copLoader.getLoadedProvider();
			return provider == null ? SightSettings.DEFAULT : provider.getSightSettings();
		};
		SightModel model = new SightModel(live, civilians, copManager, System::currentTimeMillis);
		perception.addSightFactor(model);
		return model;
	}
}
