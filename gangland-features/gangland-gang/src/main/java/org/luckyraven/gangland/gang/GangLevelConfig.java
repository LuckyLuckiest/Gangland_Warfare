package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/** Lane W5-L beans: the level table and what a level allows. Bodies arrive with that lane. */
@Configuration
public final class GangLevelConfig {

	@Bean
	public GangLimits gangLimits() {
		return new GangLimits();
	}

	@Bean
	public GangLevelTable gangLevelTable() {
		return new GangLevelTable();
	}
}
