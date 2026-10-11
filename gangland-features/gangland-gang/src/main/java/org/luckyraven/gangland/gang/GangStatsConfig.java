package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/** Lane W5-S beans: gang stats and the ranking. The body arrives with that lane. */
@Configuration
public final class GangStatsConfig {

	@Bean
	public GangStatsService gangStatsService() {
		return new GangStatsService();
	}
}
