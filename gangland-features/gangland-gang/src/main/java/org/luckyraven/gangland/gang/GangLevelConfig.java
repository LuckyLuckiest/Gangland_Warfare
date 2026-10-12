package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.level.LevelRewardRunner;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/** Lane W5-L beans: the level table, what a level allows and the level rewards. */
@Configuration
public final class GangLevelConfig {

	@Bean
	public GangLevelTable gangLevelTable(GangRules rules) {
		return new GangLevelTable(rules::getLevelRows);
	}

	@Bean
	public GangLimits gangLimits(GangLevelTable table, GangManager gangManager) {
		return new GangLimits(table, gangManager);
	}

	@Bean
	public LevelRewardRunner levelRewardRunner(GangLevelTable table, GangRules rules) {
		return new LevelRewardRunner(table, rules);
	}
}
